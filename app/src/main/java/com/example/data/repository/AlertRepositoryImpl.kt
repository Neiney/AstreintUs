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

    override fun getAlertById(id: Long): Flow<Alert?> {
        return alertDao.getAlertById(id).map { it?.toDomain() }
    }

    override suspend fun getAlertByIdSync(id: Long): Alert? {
        return alertDao.getAlertByIdSync(id)?.toDomain()
    }

    override suspend fun triggerAlertForMail(mailId: Long): Result<Alert> {
        val mail = mailDao.getMailByIdSync(mailId)
            ?: return Result.failure(IllegalArgumentException("Mail not found for id $mailId"))

        val existingAlert = alertDao.getAlertByMailUid(mail.uid)
        if (existingAlert != null) {
            return Result.success(existingAlert.toDomain())
        }

        val entity = AlertEventEntity(
            mailId = mail.id,
            mailUid = mail.uid,
            senderName = mail.senderName,
            senderAddress = mail.senderAddress,
            subject = mail.subject,
            receivedTime = mail.receivedDate,
            status = AlertStatus.PENDING.name
        )

        val id = alertDao.insertAlert(entity)
        return Result.success(entity.copy(id = id).toDomain())
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
            senderName = senderName,
            senderAddress = senderAddress,
            subject = subject,
            receivedTime = receivedTime,
            status = domainStatus,
            acknowledgedTime = acknowledgedTime,
            reactionTimeSeconds = reactionTimeSeconds,
            snoozedUntil = snoozedUntil
        )
    }
}
