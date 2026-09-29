package com.example.domain.usecase

import com.example.domain.model.Alert
import com.example.domain.model.AlertStatus
import com.example.domain.model.SnoozeOption
import com.example.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAlertRepository : AlertRepository {
    var acknowledgedAlertId: Long? = null
    var snoozedAlertId: Long? = null
    var snoozedOption: SnoozeOption? = null

    override fun getAllAlerts(): Flow<List<Alert>> = flowOf(emptyList())
    override fun getPendingAlerts(): Flow<List<Alert>> = flowOf(emptyList())
    override suspend fun getPendingAlertsSync(): List<Alert> = emptyList()
    override suspend fun getActiveAlertsSync(): List<Alert> = emptyList()
    override fun getAlertById(id: Long): Flow<Alert?> = flowOf(null)
    override suspend fun getAlertByIdSync(id: Long): Alert? = null
    override suspend fun triggerAlertForMail(mailId: Long, dedupKey: String?, reason: String?): Result<Alert> = Result.success(
        Alert(id = 1, mailId = mailId, mailUid = 10, senderName = "Sender", senderAddress = "s@test.com", subject = "Sub", receivedTime = 0)
    )
    override suspend fun muteAlert(alertId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun acknowledgeAlert(alertId: Long): Result<Unit> {
        acknowledgedAlertId = alertId
        return Result.success(Unit)
    }
    override suspend fun snoozeAlert(alertId: Long, snoozeOption: SnoozeOption): Result<Unit> {
        snoozedAlertId = alertId
        snoozedOption = snoozeOption
        return Result.success(Unit)
    }
    override suspend fun checkAndReactivateSnoozes(): Int = 0
}

class AcknowledgeAlertUseCaseTest {

    @Test
    fun `invoke calls repository acknowledgeAlert and succeeds`() = runTest {
        val fakeRepo = FakeAlertRepository()
        val useCase = AcknowledgeAlertUseCase(fakeRepo)

        val result = useCase(42L)

        assertTrue(result.isSuccess)
        assertEquals(42L, fakeRepo.acknowledgedAlertId)
    }

    @Test
    fun `reaction time formatted correctly`() {
        val alert = Alert(
            id = 1,
            mailId = 1,
            mailUid = 100,
            senderName = "Ops Team",
            senderAddress = "ops@example.com",
            subject = "Service Down",
            receivedTime = 1000L,
            status = AlertStatus.ACKNOWLEDGED,
            acknowledgedTime = 74000L,
            reactionTimeSeconds = 73L
        )

        assertEquals("1m 13s", alert.reactionTimeFormatted)
    }
}
