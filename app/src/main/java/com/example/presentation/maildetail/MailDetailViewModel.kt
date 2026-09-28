package com.example.presentation.maildetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Alert
import com.example.domain.model.Mail
import com.example.domain.model.MailStatus
import com.example.domain.model.SnoozeOption
import com.example.domain.repository.AlertRepository
import com.example.domain.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MailDetailUiState {
    data object Loading : MailDetailUiState
    data class Success(
        val mail: Mail,
        val associatedAlert: Alert? = null,
        val isHtmlMode: Boolean = false
    ) : MailDetailUiState
    data object NotFound : MailDetailUiState
}

class MailDetailViewModel(
    private val mailId: Long,
    private val mailRepository: MailRepository,
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MailDetailUiState>(MailDetailUiState.Loading)
    val uiState: StateFlow<MailDetailUiState> = _uiState.asStateFlow()

    init {
        loadMail()
    }

    private fun loadMail() {
        viewModelScope.launch {
            mailRepository.getMailById(mailId).collect { mail ->
                if (mail == null) {
                    _uiState.value = MailDetailUiState.NotFound
                } else {
                    // Check if there is an alert for this mail
                    val pendingAlerts = alertRepository.getPendingAlertsSync()
                    val alert = pendingAlerts.find { it.mailId == mail.id }

                    val currentState = _uiState.value
                    val isHtml = if (currentState is MailDetailUiState.Success) currentState.isHtmlMode else false

                    _uiState.value = MailDetailUiState.Success(
                        mail = mail,
                        associatedAlert = alert,
                        isHtmlMode = isHtml
                    )

                    // Automatically mark as READ if currently NEW
                    if (mail.status == MailStatus.NEW) {
                        mailRepository.updateMailStatus(mail.id, MailStatus.READ)
                    }
                }
            }
        }
    }

    fun toggleHtmlMode() {
        val current = _uiState.value
        if (current is MailDetailUiState.Success) {
            _uiState.value = current.copy(isHtmlMode = !current.isHtmlMode)
        }
    }

    fun toggleReadStatus() {
        val current = _uiState.value
        if (current is MailDetailUiState.Success) {
            viewModelScope.launch {
                val newStatus = if (current.mail.status == MailStatus.READ) MailStatus.NEW else MailStatus.READ
                mailRepository.updateMailStatus(current.mail.id, newStatus)
            }
        }
    }

    fun acknowledgeAlert(alertId: Long) {
        viewModelScope.launch {
            alertRepository.acknowledgeAlert(alertId)
        }
    }

    fun snoozeAlert(alertId: Long, snoozeOption: SnoozeOption) {
        viewModelScope.launch {
            alertRepository.snoozeAlert(alertId, snoozeOption)
        }
    }

    companion object {
        fun provideFactory(
            mailId: Long,
            mailRepository: MailRepository,
            alertRepository: AlertRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MailDetailViewModel(mailId, mailRepository, alertRepository) as T
            }
        }
    }
}
