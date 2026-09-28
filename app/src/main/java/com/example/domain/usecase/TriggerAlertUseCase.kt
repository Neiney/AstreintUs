package com.example.domain.usecase

import com.example.domain.model.Alert
import com.example.domain.repository.AlertRepository

class TriggerAlertUseCase(
    private val alertRepository: AlertRepository
) {
    suspend operator fun invoke(mailId: Long): Result<Alert> {
        return alertRepository.triggerAlertForMail(mailId)
    }
}
