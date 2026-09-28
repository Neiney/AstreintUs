package com.example.domain.usecase

import com.example.domain.repository.AlertRepository

class AcknowledgeAlertUseCase(
    private val alertRepository: AlertRepository
) {
    suspend operator fun invoke(alertId: Long): Result<Unit> {
        return alertRepository.acknowledgeAlert(alertId)
    }
}
