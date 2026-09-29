package com.example.data.repository

import com.example.data.local.db.MailDao
import com.example.data.local.entity.MailMessageEntity
import com.example.data.mdm.MdmConfigManager
import com.example.data.prefs.EncryptedPreferences
import com.example.data.remote.imap.ImapClient
import com.example.domain.model.Mail
import com.example.domain.model.MailStatus
import com.example.domain.repository.MailRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MailRepositoryImpl(
    private val mailDao: MailDao,
    private val imapClient: ImapClient,
    private val prefs: EncryptedPreferences
) : MailRepository {

    override fun getAllMails(): Flow<List<Mail>> {
        return mailDao.getAllMails().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMailById(id: Long): Flow<Mail?> {
        return mailDao.getMailById(id).map { it?.toDomain() }
    }

    override suspend fun getMailByIdSync(id: Long): Mail? {
        return mailDao.getMailByIdSync(id)?.toDomain()
    }

    override suspend fun getMailByUid(uid: Long): Mail? {
        return mailDao.getMailByUid(uid)?.toDomain()
    }

    override suspend fun updateMailStatus(id: Long, status: MailStatus) {
        mailDao.updateMailStatus(id, status.name)
    }

    override suspend fun fetchAndSaveNewMails(): Result<List<Mail>> {
        val config = prefs.getAccountConfig()
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("IMAP Account is not configured."))
        }

        val mailbox = config.mailbox.ifBlank { "INBOX/ONCALL" }
        val dbMaxUid = mailDao.getMaxUidForMailbox(mailbox) ?: 0L
        val prefLastUid = prefs.getLastKnownUid(mailbox)
        val sinceUid = maxOf(dbMaxUid, prefLastUid)
        val savedValidity = prefs.getFolderUidValidity(mailbox)

        val fetchResult = imapClient.fetchNewMessages(config, mailbox, sinceUid, savedValidity)
        return fetchResult.mapCatching { result ->
            // Save updated validity
            if (result.folderUidValidity > 0) {
                prefs.setFolderUidValidity(mailbox, result.folderUidValidity)
            }

            if (result.isFirstEnrollment) {
                // Initial silent enrollment: record highest UID without alerting
                prefs.setLastKnownUid(mailbox, result.latestUid)
                prefs.setInitialEnrollmentDone(mailbox, true)
                return@mapCatching emptyList()
            }

            val newlySaved = mutableListOf<Mail>()
            var highestUid = sinceUid

            for (remote in result.messages) {
                val existing = mailDao.getMailByUidAndValidity(remote.uid, result.folderUidValidity)
                if (existing == null) {
                    val entity = MailMessageEntity(
                        uid = remote.uid,
                        uidValidity = result.folderUidValidity,
                        mailbox = mailbox,
                        senderName = remote.senderName,
                        senderAddress = remote.senderAddress,
                        subject = remote.subject,
                        receivedDate = remote.receivedDate,
                        bodyExcerpt = remote.bodyExcerpt,
                        bodyText = remote.bodyText,
                        bodyHtml = remote.bodyHtml,
                        status = MailStatus.NEW.name,
                        incidentId = null
                    )
                    val insertedId = mailDao.insertMail(entity)
                    newlySaved.add(entity.copy(id = insertedId).toDomain())
                }
                if (remote.uid > highestUid) {
                    highestUid = remote.uid
                }
            }

            if (highestUid > sinceUid) {
                prefs.setLastKnownUid(mailbox, highestUid)
            }

            newlySaved
        }
    }

    override suspend fun testConnection(): Result<String> {
        val config = prefs.getAccountConfig()
        return imapClient.testConnection(config)
    }

    override suspend fun deleteAllMails() {
        mailDao.deleteAllMails()
        val mailbox = prefs.getAccountConfig().mailbox
        prefs.setLastKnownUid(mailbox, 0L)
    }

    private fun MailMessageEntity.toDomain(): Mail {
        val domainStatus = try {
            MailStatus.valueOf(status)
        } catch (_: Exception) {
            MailStatus.NEW
        }
        return Mail(
            id = id,
            uid = uid,
            uidValidity = uidValidity,
            mailbox = mailbox,
            senderName = senderName,
            senderAddress = senderAddress,
            subject = subject,
            receivedDate = receivedDate,
            bodyExcerpt = bodyExcerpt,
            bodyText = bodyText,
            bodyHtml = bodyHtml,
            status = domainStatus,
            incidentId = incidentId
        )
    }
}
