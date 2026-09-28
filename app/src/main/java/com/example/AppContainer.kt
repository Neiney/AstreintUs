package com.example

import android.content.Context
import com.example.data.local.db.AppDatabase
import com.example.data.prefs.EncryptedPreferences
import com.example.data.remote.imap.ImapClient
import com.example.data.repository.AlertRepositoryImpl
import com.example.data.repository.MailRepositoryImpl
import com.example.domain.repository.AlertRepository
import com.example.domain.repository.MailRepository
import com.example.domain.usecase.AcknowledgeAlertUseCase
import com.example.domain.usecase.FetchNewMailsUseCase
import com.example.domain.usecase.SnoozeAlertUseCase
import com.example.domain.usecase.TriggerAlertUseCase

class AppContainer(private val context: Context) {

    val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val encryptedPreferences: EncryptedPreferences by lazy {
        EncryptedPreferences(context)
    }

    val imapClient: ImapClient by lazy {
        ImapClient()
    }

    val mailRepository: MailRepository by lazy {
        MailRepositoryImpl(
            mailDao = appDatabase.mailDao(),
            imapClient = imapClient,
            prefs = encryptedPreferences
        )
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepositoryImpl(
            alertDao = appDatabase.alertDao(),
            mailDao = appDatabase.mailDao()
        )
    }

    val fetchNewMailsUseCase: FetchNewMailsUseCase by lazy {
        FetchNewMailsUseCase(mailRepository)
    }

    val triggerAlertUseCase: TriggerAlertUseCase by lazy {
        TriggerAlertUseCase(alertRepository)
    }

    val acknowledgeAlertUseCase: AcknowledgeAlertUseCase by lazy {
        AcknowledgeAlertUseCase(alertRepository)
    }

    val snoozeAlertUseCase: SnoozeAlertUseCase by lazy {
        SnoozeAlertUseCase(alertRepository)
    }
}
