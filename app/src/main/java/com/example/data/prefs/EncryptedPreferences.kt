package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EncryptedPreferences(private val context: Context) {

    private val prefs: SharedPreferences = createEncryptedSharedPreferences()

    private val _onCallModeState = MutableStateFlow(isOnCallActive())
    val onCallModeState: StateFlow<Boolean> = _onCallModeState.asStateFlow()

    private val _themeState = MutableStateFlow(getThemePreference())
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        return try {
            buildEncryptedPrefs()
        } catch (e: Exception) {
            Log.e("EncryptedPreferences", "Failed to initialize EncryptedSharedPreferences, resetting corrupted store", e)
            try {
                context.deleteSharedPreferences(PREFS_FILENAME)
            } catch (_: Exception) {}
            buildEncryptedPrefs()
        }
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveAccountConfig(config: AccountConfig) {
        prefs.edit()
            .putString(KEY_EMAIL, config.emailAddress)
            .putString(KEY_IMAP_HOST, config.imapHost)
            .putInt(KEY_IMAP_PORT, config.imapPort)
            .putString(KEY_SMTP_HOST, config.smtpHost)
            .putInt(KEY_SMTP_PORT, config.smtpPort)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_SECURITY_TYPE, config.securityType.name)
            .apply()
    }

    fun getAccountConfig(): AccountConfig {
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val imapHost = prefs.getString(KEY_IMAP_HOST, "") ?: ""
        val imapPort = prefs.getInt(KEY_IMAP_PORT, 993)
        val smtpHost = prefs.getString(KEY_SMTP_HOST, "") ?: ""
        val smtpPort = prefs.getInt(KEY_SMTP_PORT, 587)
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        val securityTypeStr = prefs.getString(KEY_SECURITY_TYPE, SecurityType.SSL_TLS.name) ?: SecurityType.SSL_TLS.name
        val securityType = try {
            SecurityType.valueOf(securityTypeStr)
        } catch (e: Exception) {
            // Insecure NONE was removed; automatically migrate to STARTTLS
            prefs.edit().putString(KEY_SECURITY_TYPE, SecurityType.STARTTLS.name).apply()
            SecurityType.STARTTLS
        }

        return AccountConfig(
            emailAddress = email,
            imapHost = imapHost,
            imapPort = imapPort,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            username = username,
            password = password,
            securityType = securityType
        )
    }

    fun clearAccountConfig() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_IMAP_HOST)
            .remove(KEY_IMAP_PORT)
            .remove(KEY_SMTP_HOST)
            .remove(KEY_SMTP_PORT)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_SECURITY_TYPE)
            .apply()
    }

    fun setOnCallActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_ON_CALL_ACTIVE, active).apply()
        _onCallModeState.value = active
    }

    fun isOnCallActive(): Boolean {
        return prefs.getBoolean(KEY_ON_CALL_ACTIVE, false)
    }

    fun setLastKnownUid(uid: Long) {
        prefs.edit().putLong(KEY_LAST_KNOWN_UID, uid).apply()
    }

    fun getLastKnownUid(): Long {
        return prefs.getLong(KEY_LAST_KNOWN_UID, 0L)
    }

    fun setRingtoneUri(uri: String) {
        prefs.edit().putString(KEY_RINGTONE_URI, uri).apply()
    }

    fun getRingtoneUri(): String {
        return prefs.getString(KEY_RINGTONE_URI, "") ?: ""
    }

    fun setThemePreference(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        _themeState.value = theme
    }

    fun getThemePreference(): String {
        return prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
    }

    fun setBatteryPromptDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_DISMISSED, dismissed).apply()
    }

    fun isBatteryPromptDismissed(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_DISMISSED, false)
    }

    companion object {
        private const val TAG = "EncryptedPreferences"
        private const val PREFS_FILENAME = "asteintus_secure_prefs"

        private const val KEY_EMAIL = "email_address"
        private const val KEY_IMAP_HOST = "imap_host"
        private const val KEY_IMAP_PORT = "imap_port"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SECURITY_TYPE = "security_type"

        private const val KEY_ON_CALL_ACTIVE = "on_call_active"
        private const val KEY_LAST_KNOWN_UID = "last_known_uid"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_THEME = "app_theme"
        private const val KEY_BATTERY_DISMISSED = "battery_dismissed"
    }
}
