package com.example.data.repository

import com.example.data.local.db.AlertDao
import com.example.data.local.db.MailDao
import com.example.data.local.entity.AlertEventEntity
import com.example.domain.model.Alert
import com.example.domain.model.AlertStatus
import com.example.domain.model.MailStatus
import com.example.domain.model.SnoozeOption
import com.example.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlertRepositoryImpl(
    private val alertDao: AlertDao,
    private val mailDao: MailDao
) : AlertRepository {

    override fun getAllAlerts(): Flow<List<Alert>> {
        return alertDao.getAllAlerts().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getPendingAlerts(): Flow<List<Alert>> {
        return alertDao.getPendingAlerts().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getPendingAlertsSync(): List<Alert> {
        return alertDao.getPendingAlertsSync().map { it.toDomain() }
    }

    override suspend fun getActiveAlertsSync(): List<Alert> {
        return alertDao.getActiveAlertsSync().map { it.toDomain() }
    }

    override fun getAlertById(id: Long): Flow<Alert?> {
        return alertDao.getAlertById(id).map { it?.toDomain() }
    }

    override suspend fun getAlertByIdSync(id: Long): Alert? {
        return alertDao.getAlertByIdSync(id)?.toDomain()
    }

    override suspend fun triggerAlertForMail(mailId: Long, dedupKey: String?, reason: String?): Result<Alert> {
        val mail = mailDao.getMailByIdSync(mailId)
            ?: return Result.failure(IllegalArgumentException("Mail not found for id $mailId"))

        // Deduplication check
        val effectiveDedup = dedupKey ?: "uid:${mail.uidValidity}:${mail.uid}"
        val existingDedup = alertDao.getAlertByDedupKey(effectiveDedup)
        if (existingDedup != null) {
            // Already created for this deduplication key
            return Result.success(existingDedup.toDomain())
        }

        val existingByUid = alertDao.getAlertByMailUid(mail.uid)
        if (existingByUid != null) {
            return Result.success(existingByUid.toDomain())
        }

        val entity = AlertEventEntity(
            mailId = mail.id,
            mailUid = mail.uid,
            uidValidity = mail.uidValidity,
            mailbox = mail.mailbox,
            senderName = mail.senderName,
            senderAddress = mail.senderAddress,
            subject = mail.subject,
            receivedTime = mail.receivedDate,
            status = AlertStatus.PENDING.name,
            incidentId = mail.incidentId,
            dedupKey = effectiveDedup,
            qualificationReason = reason ?: "Qualifié conforme"
        )

        val id = alertDao.insertAlert(entity)
        return Result.success(entity.copy(id = id).toDomain())
    }

    override suspend fun muteAlert(alertId: Long): Result<Unit> {
        alertDao.updateAlertStatus(alertId, AlertStatus.MUTED.name)
        return Result.success(Unit)
    }

    override suspend fun acknowledgeAlert(alertId: Long): Result<Unit> {
        val alert = alertDao.getAlertByIdSync(alertId)
            ?: return Result.failure(IllegalArgumentException("Alert not found for id $alertId"))

        val now = System.currentTimeMillis()
        val reactionSeconds = maxOf(0L, (now - alert.receivedTime) / 1000L)

        alertDao.acknowledgeAlert(alertId, now, reactionSeconds)
        mailDao.updateMailStatus(alert.mailId, MailStatus.ACKNOWLEDGED.name)
        return Result.success(Unit)
    }

    override suspend fun snoozeAlert(alertId: Long, snoozeOption: SnoozeOption): Result<Unit> {
        val alert = alertDao.getAlertByIdSync(alertId)
            ?: return Result.failure(IllegalArgumentException("Alert not found for id $alertId"))

        val snoozedUntil = System.currentTimeMillis() + snoozeOption.durationMillis
        alertDao.snoozeAlert(alertId, snoozedUntil)
        mailDao.updateMailStatus(alert.mailId, MailStatus.SNOOZED.name)
        return Result.success(Unit)
    }

    override suspend fun checkAndReactivateSnoozes(): Int {
        return alertDao.reactivateExpiredSnoozes(System.currentTimeMillis())
    }

    private fun AlertEventEntity.toDomain(): Alert {
        val domainStatus = try {
            AlertStatus.valueOf(status)
        } catch (_: Exception) {
            AlertStatus.PENDING
        }
        return Alert(
            id = id,
            mailId = mailId,
            mailUid = mailUid,
            uidValidity = uidValidity,
            mailbox = mailbox,
            senderName = senderName,
            senderAddress = senderAddress,
            subject = subject,
            receivedTime = receivedTime,
            status = domainStatus,
            acknowledgedTime = acknowledgedTime,
            reactionTimeSeconds = reactionTimeSeconds,
            snoozedUntil = snoozedUntil,
            incidentId = incidentId,
            dedupKey = dedupKey,
            qualificationReason = qualificationReason
        )
    }
}
