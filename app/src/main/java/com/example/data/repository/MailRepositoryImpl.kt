package com.example.data.repository

import com.example.data.local.db.MailDao
import com.example.data.local.entity.MailMessageEntity
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

        val dbMaxUid = mailDao.getMaxUid() ?: 0L
        val prefLastUid = prefs.getLastKnownUid()
        val sinceUid = maxOf(dbMaxUid, prefLastUid)

        val fetchResult = imapClient.fetchNewMessages(config, sinceUid)
        return fetchResult.mapCatching { remoteList ->
            val newlySaved = mutableListOf<Mail>()
            var highestUid = sinceUid

            for (remote in remoteList) {
                // Check if already in DB
                val existing = mailDao.getMailByUid(remote.uid)
                if (existing == null) {
                    val entity = MailMessageEntity(
                        uid = remote.uid,
                        senderName = remote.senderName,
                        senderAddress = remote.senderAddress,
                        subject = remote.subject,
                        receivedDate = remote.receivedDate,
                        bodyExcerpt = remote.bodyExcerpt,
                        bodyText = remote.bodyText,
                        bodyHtml = remote.bodyHtml,
                        status = MailStatus.NEW.name
                    )
                    val insertedId = mailDao.insertMail(entity)
                    newlySaved.add(entity.copy(id = insertedId).toDomain())
                }
                if (remote.uid > highestUid) {
                    highestUid = remote.uid
                }
            }

            if (highestUid > sinceUid) {
                prefs.setLastKnownUid(highestUid)
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
        prefs.setLastKnownUid(0L)
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
            senderName = senderName,
            senderAddress = senderAddress,
            subject = subject,
            receivedDate = receivedDate,
            bodyExcerpt = bodyExcerpt,
            bodyText = bodyText,
            bodyHtml = bodyHtml,
            status = domainStatus
        )
    }
}
