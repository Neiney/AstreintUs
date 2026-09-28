package com.example.data.remote.imap

import android.util.Log
import com.example.data.prefs.AccountConfig
import com.example.data.prefs.SecurityType
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

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

            folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            val messageCount = folder.messageCount

            Result.success("Connection successful! Connected to INBOX with $messageCount messages.")
        } catch (e: AuthenticationFailedException) {
            Log.e(TAG, "IMAP Authentication failed", e)
            Result.failure(Exception("Authentication failed. Please verify your username and password."))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown IMAP host", e)
            Result.failure(Exception("Could not resolve host: ${config.imapHost}. Check the server address."))
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "IMAP Connection timed out", e)
            Result.failure(Exception("Connection timed out to ${config.imapHost}:${config.imapPort}."))
        } catch (e: MessagingException) {
            Log.e(TAG, "IMAP MessagingException", e)
            Result.failure(Exception("IMAP error: ${e.message ?: "Could not connect to mailbox."}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error testing IMAP connection", e)
            Result.failure(Exception("Connection error: ${e.localizedMessage ?: "Unknown failure."}"))
        } finally {
            try {
                folder?.close(false)
            } catch (_: Exception) {}
            try {
                store?.close()
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchNewMessages(
        config: AccountConfig,
        sinceUid: Long
    ): Result<List<RemoteMailMessage>> = withContext(Dispatchers.IO) {
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

            val rawFolder = store.getFolder("INBOX")
            if (rawFolder !is IMAPFolder) {
                return@withContext Result.failure(Exception("Folder does not support UID operations."))
            }
            folder = rawFolder
            folder.open(Folder.READ_ONLY)

            val messages: Array<Message> = if (sinceUid > 0) {
                // Fetch messages with UID > sinceUid
                folder.getMessagesByUID(sinceUid + 1, UIDFolder.LASTUID)
            } else {
                // First sync: fetch the latest 10 messages so the mailbox isn't empty
                val total = folder.messageCount
                if (total <= 0) {
                    emptyArray()
                } else {
                    val start = maxOf(1, total - 9)
                    folder.getMessages(start, total)
                }
            }

            val resultList = mutableListOf<RemoteMailMessage>()

            for (msg in messages) {
                val uid = folder.getUID(msg)
                if (sinceUid > 0 && uid <= sinceUid) continue

                val senderInfo = parseSender(msg)
                val subject = msg.subject ?: "(No Subject)"
                val receivedDate = (msg.receivedDate ?: msg.sentDate)?.time ?: System.currentTimeMillis()

                val extracted = extractContent(msg)
                val bodyText = extracted.plainText.ifBlank { extracted.htmlText.replace(Regex("<[^>]*>"), " ").trim() }
                val excerpt = bodyText.take(200).replace("\n", " ").trim()

                resultList.add(
                    RemoteMailMessage(
                        uid = uid,
                        senderName = senderInfo.first,
                        senderAddress = senderInfo.second,
                        subject = subject,
                        receivedDate = receivedDate,
                        bodyExcerpt = excerpt,
                        bodyText = bodyText,
                        bodyHtml = extracted.htmlText.ifBlank { null }
                    )
                )
            }

            Result.success(resultList.sortedByDescending { it.receivedDate })
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
