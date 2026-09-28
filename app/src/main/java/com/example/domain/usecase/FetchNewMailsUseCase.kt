package com.example.domain.usecase

import com.example.domain.model.Mail
import com.example.domain.repository.MailRepository

class FetchNewMailsUseCase(
    private val mailRepository: MailRepository
) {
    suspend operator fun invoke(): Result<List<Mail>> {
        return mailRepository.fetchAndSaveNewMails()
    }
}
