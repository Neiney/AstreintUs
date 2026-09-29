package com.example.data.remote.imap

import android.util.Log
import com.example.data.prefs.AccountConfig
import com.example.data.prefs.SecurityType
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Folder
import javax.mail.Header
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.InternetAddress
import kotlin.coroutines.coroutineContext

data class FetchResult(
    val messages: List<RemoteMailMessage>,
    val folderUidValidity: Long,
    val latestUid: Long,
    val isFirstEnrollment: Boolean
)

class ImapClient {

    suspend fun testConnection(config: AccountConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalArgumentException("Account configuration is incomplete."))
        }

        var store: Store? = null
        var folder: Folder? = null
        try {
            val session = createSession(config)
            val protocol = if (config.securityType == SecurityType.SSL_TLS) "imaps" else "imap"
            store = session.getStore(protocol)
            store.connect(config.imapHost, config.imapPort, config.username, config.password)

            val targetMailbox = config.mailbox.ifBlank { "INBOX/ONCALL" }
            folder = try {
                store.getFolder(targetMailbox).also { it.open(Folder.READ_ONLY) }
            } catch (e: Exception) {
                Log.w(TAG, "Dedicated folder '$targetMailbox' not found, falling back to INBOX for test")
                store.getFolder("INBOX").also { it.open(Folder.READ_ONLY) }
            }

            val messageCount = folder.messageCount
            val validity = if (folder is UIDFolder) folder.uidValidity else 0L

            Result.success("Connexion réussie ! Dossier '${folder.name}' accessible ($messageCount messages, UIDVALIDITY=$validity).")
        } catch (e: AuthenticationFailedException) {
            Log.e(TAG, "IMAP Authentication failed", e)
            Result.failure(Exception("Échec d'authentification. Veuillez vérifier l'identifiant et le mot de passe."))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown IMAP host", e)
            Result.failure(Exception("Hôte introuvable : ${config.imapHost}. Vérifiez l'adresse du serveur."))
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "IMAP Connection timed out", e)
            Result.failure(Exception("Délai de connexion dépassé vers ${config.imapHost}:${config.imapPort}."))
        } catch (e: MessagingException) {
            Log.e(TAG, "IMAP MessagingException", e)
            Result.failure(Exception("Erreur IMAP : ${e.message ?: "Impossible d'accéder au dossier."}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error testing IMAP connection", e)
            Result.failure(Exception("Erreur de connexion : ${e.localizedMessage ?: "Échec inconnu."}"))
        } finally {
            try {
                folder?.close(false)
            } catch (_: Exception) {}
            try {
                store?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Synchronizes messages from the dedicated mailbox.
     * Enforces the Initial Silent Enrollment rule: never alert on existing history.
     */
    suspend fun fetchNewMessages(
        config: AccountConfig,
        mailbox: String,
        sinceUid: Long,
        savedUidValidity: Long
    ): Result<FetchResult> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Account is not configured."))
        }

        var store: Store? = null
        var folder: IMAPFolder? = null
        try {
            val session = createSession(config)
            val protocol = if (config.securityType == SecurityType.SSL_TLS) "imaps" else "imap"
            store = session.getStore(protocol)
            store.connect(config.imapHost, config.imapPort, config.username, config.password)

            val targetName = mailbox.ifBlank { "INBOX/ONCALL" }
            val rawFolder = store.getFolder(targetName)
            if (rawFolder !is IMAPFolder) {
                return@withContext Result.failure(Exception("Folder '$targetName' does not support IMAP UID operations."))
            }
            folder = rawFolder
            folder.open(Folder.READ_ONLY)

            val currentValidity = folder.uidValidity
            val totalCount = folder.messageCount

            // Check if UIDVALIDITY changed or this is first enrollment
            val isValidityReset = savedUidValidity > 0 && currentValidity != savedUidValidity
            val isFirstEnrollment = sinceUid <= 0L || isValidityReset

            if (isFirstEnrollment) {
                // Initial silent enrollment: record highest current UID without alerting on past messages!
                val highestUid = if (totalCount > 0) {
                    val lastMsg = folder.getMessage(totalCount)
                    folder.getUID(lastMsg)
                } else {
                    0L
                }
                Log.i(TAG, "Silent initial enrollment on '$targetName': highest UID = $highestUid (validity=$currentValidity). No historical alerts.")
                return@withContext Result.success(
                    FetchResult(
                        messages = emptyList(),
                        folderUidValidity = currentValidity,
                        latestUid = highestUid,
                        isFirstEnrollment = true
                    )
                )
            }

            // Normal fetch: messages with UID > sinceUid
            val messages: Array<Message> = folder.getMessagesByUID(sinceUid + 1, UIDFolder.LASTUID)
            val resultList = mutableListOf<RemoteMailMessage>()
            var maxSeenUid = sinceUid

            for (msg in messages) {
                val uid = folder.getUID(msg)
                if (uid <= sinceUid) continue
                if (uid > maxSeenUid) maxSeenUid = uid

                val senderInfo = parseSender(msg)
                val subject = msg.subject ?: "(No Subject)"
                val receivedDate = (msg.receivedDate ?: msg.sentDate)?.time ?: System.currentTimeMillis()
                val headers = extractHeaders(msg)

                val extracted = extractContent(msg)
                val bodyText = extracted.plainText.ifBlank { extracted.htmlText.replace(Regex("<[^>]*>"), " ").trim() }
                val excerpt = bodyText.take(200).replace("\n", " ").trim()

                resultList.add(
                    RemoteMailMessage(
                        uid = uid,
                        uidValidity = currentValidity,
                        mailbox = targetName,
                        senderName = senderInfo.first,
                        senderAddress = senderInfo.second,
                        subject = subject,
                        receivedDate = receivedDate,
                        bodyExcerpt = excerpt,
                        bodyText = bodyText,
                        bodyHtml = extracted.htmlText.ifBlank { null },
                        headers = headers
                    )
                )
            }

            Result.success(
                FetchResult(
                    messages = resultList.sortedByDescending { it.receivedDate },
                    folderUidValidity = currentValidity,
                    latestUid = maxSeenUid,
                    isFirstEnrollment = false
                )
            )
        } catch (e: AuthenticationFailedException) {
            Log.e(TAG, "Sync failed: Authentication error", e)
            Result.failure(Exception("Sync failed: Authentication error."))
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(Exception("Sync failed: ${e.localizedMessage ?: "IMAP error"}"))
        } finally {
            try {
                folder?.close(false)
            } catch (_: Exception) {}
            try {
                store?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Maintains an IMAP IDLE connection for instantaneous low-latency alert reception (< 3 min SLA).
     * Re-issues IDLE every 15 minutes or upon message arrival.
     */
    suspend fun runIdleLoop(
        config: AccountConfig,
        mailbox: String,
        onNewMessageReceived: suspend () -> Unit,
        onHeartbeatHealthy: suspend () -> Unit
    ) = withContext(Dispatchers.IO) {
        var store: Store? = null
        var folder: IMAPFolder? = null

        try {
            val session = createSession(config)
            val protocol = if (config.securityType == SecurityType.SSL_TLS) "imaps" else "imap"
            store = session.getStore(protocol)
            store.connect(config.imapHost, config.imapPort, config.username, config.password)

            val targetName = mailbox.ifBlank { "INBOX/ONCALL" }
            val raw = store.getFolder(targetName)
            if (raw !is IMAPFolder) {
                throw IllegalStateException("Folder '$targetName' does not support IMAP IDLE.")
            }
            folder = raw
            folder.open(Folder.READ_ONLY)

            folder.addMessageCountListener(object : MessageCountAdapter() {
                override fun messagesAdded(e: MessageCountEvent?) {
                    Log.i(TAG, "IMAP IDLE event: new message(s) added to '$targetName'")
                }
            })

            Log.i(TAG, "Entering IMAP IDLE on folder '$targetName' (SSL=${config.securityType})...")

            while (coroutineContext.isActive && store.isConnected && folder.isOpen) {
                // Heartbeat to confirm SLA
                onHeartbeatHealthy()

                // Check messages right before idling
                onNewMessageReceived()

                // Block in IDLE until incoming event or server timeout
                try {
                    folder.idle()
                } catch (e: Exception) {
                    if (!coroutineContext.isActive) break
                    Log.w(TAG, "IDLE interrupted or timed out, refreshing IDLE state", e)
                }

                // Immediately trigger message processing after waking up from IDLE
                onNewMessageReceived()
                onHeartbeatHealthy()
            }
        } finally {
            try {
                folder?.close(false)
            } catch (_: Exception) {}
            try {
                store?.close()
            } catch (_: Exception) {}
        }
    }

    private fun extractHeaders(msg: Message): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val headersEnum = msg.allHeaders
            while (headersEnum != null && headersEnum.hasMoreElements()) {
                val h = headersEnum.nextElement()
                if (h is Header) {
                    map[h.name] = h.value ?: ""
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting message headers", e)
        }
        return map
    }

    private fun createSession(config: AccountConfig): Session {
        val props = Properties()
        val isSsl = config.securityType == SecurityType.SSL_TLS
        val isStartTls = config.securityType == SecurityType.STARTTLS

        if (isSsl) {
            props["mail.imaps.host"] = config.imapHost
            props["mail.imaps.port"] = config.imapPort.toString()
            props["mail.imaps.ssl.enable"] = "true"
            props["mail.imaps.ssl.checkserveridentity"] = "true"
            props["mail.imaps.connectiontimeout"] = "15000"
            props["mail.imaps.timeout"] = "15000"
        } else {
            props["mail.imap.host"] = config.imapHost
            props["mail.imap.port"] = config.imapPort.toString()
            props["mail.imap.connectiontimeout"] = "15000"
            props["mail.imap.timeout"] = "15000"
            if (isStartTls) {
                props["mail.imap.starttls.enable"] = "true"
                props["mail.imap.starttls.required"] = "true"
                props["mail.imap.ssl.checkserveridentity"] = "true"
            } else {
                throw IllegalArgumentException(
                    "Insecure plaintext IMAP connection rejected by security policy."
                )
            }
        }
        return Session.getInstance(props, null)
    }

    private fun parseSender(msg: Message): Pair<String, String> {
        val fromArray = msg.from
        if (fromArray.isNullOrEmpty()) {
            return Pair("Unknown Sender", "unknown@example.com")
        }
        val address = fromArray[0]
        return if (address is InternetAddress) {
            val personal = address.personal ?: ""
            val addr = address.address ?: ""
            Pair(personal.ifBlank { addr }, addr)
        } else {
            Pair(address.toString(), address.toString())
        }
    }

    private data class ExtractedContent(val plainText: String, val htmlText: String)

    private fun extractContent(part: Part): ExtractedContent {
        var plain = ""
        var html = ""

        try {
            if (part.isMimeType("text/plain")) {
                val content = part.content
                if (content is String) {
                    plain += content
                }
            } else if (part.isMimeType("text/html")) {
                val content = part.content
                if (content is String) {
                    html += content
                }
            } else if (part.isMimeType("multipart/*")) {
                val mp = part.content as? Multipart
                if (mp != null) {
                    for (i in 0 until mp.count) {
                        val bp = mp.getBodyPart(i)
                        val sub = extractContent(bp)
                        if (sub.plainText.isNotBlank()) plain += "\n" + sub.plainText
                        if (sub.htmlText.isNotBlank()) html += "\n" + sub.htmlText
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting message part content", e)
        }

        return ExtractedContent(plain.trim(), html.trim())
    }

    companion object {
        private const val TAG = "ImapClient"
    }
}
