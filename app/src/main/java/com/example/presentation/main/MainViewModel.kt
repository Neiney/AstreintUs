package com.example.presentation.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.mdm.MdmConfigManager
import com.example.data.prefs.EncryptedPreferences
import com.example.domain.model.Alert
import com.example.domain.model.HealthState
import com.example.domain.model.HealthTelemetry
import com.example.domain.model.Mail
import com.example.domain.repository.AlertRepository
import com.example.domain.repository.MailRepository
import com.example.service.ActiveAlertState
import com.example.service.AlertService
import com.example.util.PostureChecker
import com.example.util.PostureReport
import com.example.worker.MailSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val isOnCallActive: Boolean = false,
    val isAccountConfigured: Boolean = false,
    val pendingAlertsCount: Int = 0,
    val totalMailsCount: Int = 0,
    val activeAlert: ActiveAlertState = ActiveAlertState(),
    val healthTelemetry: HealthTelemetry = HealthTelemetry(),
    val postureReport: PostureReport? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val showBatteryPrompt: Boolean = false,
    val manufacturer: String = Build.MANUFACTURER
)

class MainViewModel(
    private val context: Context,
    private val prefs: EncryptedPreferences,
    private val mailRepository: MailRepository,
    private val alertRepository: AlertRepository
) : ViewModel() {

    val pendingAlerts: StateFlow<List<Alert>> = alertRepository.getPendingAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentMails: StateFlow<List<Mail>> = mailRepository.getAllMails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAlertState: StateFlow<ActiveAlertState> = AlertService.activeAlertState
    val healthTelemetryState: StateFlow<HealthTelemetry> = AlertService.healthTelemetryFlow

    private val _uiState = MutableStateFlow(
        MainUiState(
            isOnCallActive = prefs.isOnCallActive(),
            isAccountConfigured = prefs.getAccountConfig().isConfigured,
            healthTelemetry = AlertService.healthTelemetryFlow.value,
            postureReport = PostureChecker.checkPosture(context),
            showBatteryPrompt = shouldShowBatteryPrompt()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.onCallModeState.collect { active ->
                _uiState.value = _uiState.value.copy(isOnCallActive = active)
            }
        }
        viewModelScope.launch {
            pendingAlerts.collect { alerts ->
                _uiState.value = _uiState.value.copy(pendingAlertsCount = alerts.size)
            }
        }
        viewModelScope.launch {
            recentMails.collect { mails ->
                _uiState.value = _uiState.value.copy(totalMailsCount = mails.size)
            }
        }
        viewModelScope.launch {
            activeAlertState.collect { alertState ->
                _uiState.value = _uiState.value.copy(activeAlert = alertState)
            }
        }
        viewModelScope.launch {
            healthTelemetryState.collect { telemetry ->
                _uiState.value = _uiState.value.copy(healthTelemetry = telemetry)
            }
        }
    }

    fun refreshConfigState() {
        val configured = prefs.getAccountConfig().isConfigured
        val posture = PostureChecker.checkPosture(context)
        _uiState.value = _uiState.value.copy(
            isAccountConfigured = configured,
            postureReport = posture,
            showBatteryPrompt = shouldShowBatteryPrompt()
        )
    }

    fun toggleOnCallMode(active: Boolean) {
        prefs.setOnCallActive(active)
        _uiState.value = _uiState.value.copy(isOnCallActive = active)

        if (active) {
            val serviceIntent = Intent(context, AlertService::class.java).apply {
                this.action = AlertService.ACTION_START_ON_CALL_NOTIFICATION
            }
            context.startForegroundService(serviceIntent)
            MailSyncWorker.startPeriodicSync(context)
        } else {
            MailSyncWorker.cancelSync(context)
            val serviceIntent = Intent(context, AlertService::class.java).apply {
                this.action = AlertService.ACTION_STOP_ALL
            }
            context.startService(serviceIntent)
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            val result = mailRepository.fetchAndSaveNewMails()
            if (result.isSuccess) {
                val newMails = result.getOrDefault(emptyList())
                val message = if (newMails.isEmpty()) "Dossier d'astreinte à jour." else "${newMails.size} nouveau(x) message(s) reçu(s)."
                _uiState.value = _uiState.value.copy(isSyncing = false, syncMessage = message)

                if (prefs.isOnCallActive()) {
                    for (mail in newMails) {
                        alertRepository.triggerAlertForMail(mail.id).onSuccess { alert ->
                            val alertIntent = Intent(context, AlertService::class.java).apply {
                                action = AlertService.ACTION_TRIGGER_ALERT
                                putExtra(AlertService.EXTRA_ALERT_ID, alert.id)
                                putExtra(AlertService.EXTRA_MAIL_ID, mail.id)
                            }
                            context.startForegroundService(alertIntent)
                        }
                    }
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Erreur de synchronisation"
                _uiState.value = _uiState.value.copy(isSyncing = false, syncMessage = "Échec de synchronisation: $err")
            }
        }
    }

    fun dismissBatteryPrompt() {
        prefs.setBatteryPromptDismissed(true)
        _uiState.value = _uiState.value.copy(showBatteryPrompt = false)
    }

    fun quickAcknowledge(alertId: Long) {
        val intent = Intent(context, AlertService::class.java).apply {
            action = AlertService.ACTION_ACKNOWLEDGE
            putExtra(AlertService.EXTRA_ALERT_ID, alertId)
        }
        context.startService(intent)
    }

    private fun shouldShowBatteryPrompt(): Boolean {
        if (prefs.isBatteryPromptDismissed()) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        return !isIgnoring
    }

    companion object {
        fun provideFactory(
            context: Context,
            prefs: EncryptedPreferences,
            mailRepository: MailRepository,
            alertRepository: AlertRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(context, prefs, mailRepository, alertRepository) as T
            }
        }
    }
}
