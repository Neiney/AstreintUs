package com.example.domain.usecase

import com.example.domain.model.SnoozeOption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeAlertUseCaseTest {

    @Test
    fun `invoke calls repository snoozeAlert with correct snooze option`() = runTest {
        val fakeRepo = FakeAlertRepository()
        val useCase = SnoozeAlertUseCase(fakeRepo)

        val result = useCase(10L, SnoozeOption.FIFTEEN_MINUTES)

        assertTrue(result.isSuccess)
        assertEquals(10L, fakeRepo.snoozedAlertId)
        assertEquals(SnoozeOption.FIFTEEN_MINUTES, fakeRepo.snoozedOption)
    }

    @Test
    fun `snooze durations are correctly computed`() {
        assertEquals(5 * 60 * 1000L, SnoozeOption.FIVE_MINUTES.durationMillis)
        assertEquals(15 * 60 * 1000L, SnoozeOption.FIFTEEN_MINUTES.durationMillis)
        assertEquals(30 * 60 * 1000L, SnoozeOption.THIRTY_MINUTES.durationMillis)
    }
}
