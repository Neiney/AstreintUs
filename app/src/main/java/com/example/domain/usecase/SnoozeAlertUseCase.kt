package com.example.domain.usecase

import com.example.domain.model.SnoozeOption
import com.example.domain.repository.AlertRepository

class SnoozeAlertUseCase(
    private val alertRepository: AlertRepository
) {
    suspend operator fun invoke(alertId: Long, snoozeOption: SnoozeOption): Result<Unit> {
        return alertRepository.snoozeAlert(alertId, snoozeOption)
    }
}
