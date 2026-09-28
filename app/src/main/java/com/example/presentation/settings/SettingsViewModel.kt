package com.example.presentation.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.prefs.AccountConfig
import com.example.data.prefs.EncryptedPreferences
import com.example.data.remote.imap.ImapClient
import com.example.domain.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accountConfig: AccountConfig = AccountConfig(),
    val ringtoneTitle: String = "Default Alarm Ringtone",
    val ringtoneUri: String = "",
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null,
    val isTestSuccess: Boolean = false,
    val isPlayingPreview: Boolean = false,
    val themeMode: String = "SYSTEM" // "LIGHT", "DARK", "SYSTEM"
)

class SettingsViewModel(
    private val context: Context,
    private val prefs: EncryptedPreferences,
    private val imapClient: ImapClient,
    private val mailRepository: MailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var previewPlayer: MediaPlayer? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        val config = prefs.getAccountConfig()
        val customUri = prefs.getRingtoneUri()
        val title = if (customUri.isNotBlank()) {
            try {
                val ringtone = RingtoneManager.getRingtone(context, Uri.parse(customUri))
                ringtone?.getTitle(context) ?: "Custom Alarm"
            } catch (_: Exception) {
                "Selected Ringtone"
            }
        } else {
            "Default System Alarm"
        }

        _uiState.value = _uiState.value.copy(
            accountConfig = config,
            ringtoneTitle = title,
            ringtoneUri = customUri,
            connectionTestResult = null
        )
    }

    fun saveAccountConfig(config: AccountConfig) {
        prefs.saveAccountConfig(config)
        _uiState.value = _uiState.value.copy(accountConfig = config)
    }

    fun testConnection(config: AccountConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingConnection = true,
                connectionTestResult = null
            )
            val result = imapClient.testConnection(config)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = result.getOrDefault("Connection successful! Authenticated with IMAP server."),
                    isTestSuccess = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = "Connection failed: ${result.exceptionOrNull()?.localizedMessage}",
                    isTestSuccess = false
                )
            }
        }
    }

    fun setRingtoneUri(uri: String, title: String) {
        prefs.setRingtoneUri(uri)
        _uiState.value = _uiState.value.copy(ringtoneUri = uri, ringtoneTitle = title)
    }

    fun toggleRingtonePreview() {
        if (_uiState.value.isPlayingPreview) {
            stopPreview()
        } else {
            startPreview()
        }
    }

    private fun startPreview() {
        stopPreview()
        try {
            val uriStr = _uiState.value.ringtoneUri
            val soundUri = if (uriStr.isNotBlank()) {
                Uri.parse(uriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            previewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                setDataSource(context, soundUri)
                setOnCompletionListener {
                    _uiState.value = _uiState.value.copy(isPlayingPreview = false)
                }
                prepare()
                start()
            }
            _uiState.value = _uiState.value.copy(isPlayingPreview = true)
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(isPlayingPreview = false)
        }
    }

    private fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        _uiState.value = _uiState.value.copy(isPlayingPreview = false)
    }

    fun clearAllData() {
        viewModelScope.launch {
            mailRepository.deleteAllMails()
            prefs.setLastKnownUid(0L)
            loadSettings()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPreview()
    }

    companion object {
        fun provideFactory(
            context: Context,
            prefs: EncryptedPreferences,
            imapClient: ImapClient,
            mailRepository: MailRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(context, prefs, imapClient, mailRepository) as T
            }
        }
    }
}
