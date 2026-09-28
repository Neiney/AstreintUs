package com.example.presentation.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Alert
import com.example.domain.model.SnoozeOption
import com.example.domain.repository.AlertRepository
import com.example.service.AlertService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlertUiState {
    data object Loading : AlertUiState
    data class Active(
        val alert: Alert,
        val isRinging: Boolean,
        val isMuted: Boolean,
        val queueCount: Int
    ) : AlertUiState
    data object Dismissed : AlertUiState
}

class AlertViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertUiState>(AlertUiState.Loading)
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            AlertService.activeAlertState.collect { serviceState ->
                if (serviceState.alertId > 0) {
                    val alert = alertRepository.getAlertByIdSync(serviceState.alertId)
                    if (alert != null) {
                        _uiState.value = AlertUiState.Active(
                            alert = alert,
                            isRinging = serviceState.isRinging,
                            isMuted = serviceState.isMuted,
                            queueCount = serviceState.queueSize
                        )
                    } else {
                        // Alert object not found in DB yet, construct from service metadata
                        _uiState.value = AlertUiState.Active(
                            alert = Alert(
                                id = serviceState.alertId,
                                mailId = serviceState.mailId,
                                mailUid = 0L,
                                senderName = serviceState.senderName,
                                senderAddress = serviceState.senderAddress,
                                subject = serviceState.subject,
                                receivedTime = serviceState.receivedTime
                            ),
                            isRinging = serviceState.isRinging,
                            isMuted = serviceState.isMuted,
                            queueCount = serviceState.queueSize
                        )
                    }
                } else if (_uiState.value !is AlertUiState.Loading) {
                    _uiState.value = AlertUiState.Dismissed
                }
            }
        }
    }

    fun loadAlert(alertId: Long) {
        viewModelScope.launch {
            val alert = alertRepository.getAlertByIdSync(alertId)
            if (alert != null) {
                val currentService = AlertService.activeAlertState.value
                _uiState.value = AlertUiState.Active(
                    alert = alert,
                    isRinging = currentService.isRinging,
                    isMuted = currentService.isMuted,
                    queueCount = currentService.queueSize
                )
            }
        }
    }

    companion object {
        fun provideFactory(alertRepository: AlertRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlertViewModel(alertRepository) as T
                }
            }
    }
}
