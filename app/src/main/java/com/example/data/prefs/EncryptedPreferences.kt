package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.mdm.MdmConfigManager
import com.example.domain.model.HealthState
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
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, resetting corrupted store", e)
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
            .putString(KEY_MAILBOX, config.mailbox)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_SECURITY_TYPE, config.securityType.name)
            .apply()
    }

    fun getAccountConfig(): AccountConfig {
        // Check MDM overrides first
        val mdmHost = MdmConfigManager.getMdmHost(context)
        val mdmPort = MdmConfigManager.getMdmPort(context)
        val mdmMailbox = MdmConfigManager.getMdmMailbox(context)
        val isMdmManaged = MdmConfigManager.isDeviceManaged(context)

        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val imapHost = mdmHost ?: (prefs.getString(KEY_IMAP_HOST, "") ?: "")
        val imapPort = mdmPort ?: prefs.getInt(KEY_IMAP_PORT, 993)
        val mailbox = mdmMailbox ?: (prefs.getString(KEY_MAILBOX, "INBOX/ONCALL") ?: "INBOX/ONCALL")
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        val securityTypeStr = prefs.getString(KEY_SECURITY_TYPE, SecurityType.SSL_TLS.name) ?: SecurityType.SSL_TLS.name
        val securityType = try {
            SecurityType.valueOf(securityTypeStr)
        } catch (e: Exception) {
            prefs.edit().putString(KEY_SECURITY_TYPE, SecurityType.SSL_TLS.name).apply()
            SecurityType.SSL_TLS
        }

        return AccountConfig(
            emailAddress = email,
            imapHost = imapHost,
            imapPort = imapPort,
            mailbox = mailbox,
            username = username,
            password = password,
            securityType = securityType,
            isMdmLocked = isMdmManaged
        )
    }

    fun clearAccountConfig() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_IMAP_HOST)
            .remove(KEY_IMAP_PORT)
            .remove(KEY_MAILBOX)
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

    // UID and UIDVALIDITY tracking per mailbox (Lot 2 & Lot 3)
    fun setLastKnownUid(mailbox: String, uid: Long) {
        prefs.edit().putLong("${KEY_LAST_KNOWN_UID}_$mailbox", uid).apply()
    }

    fun getLastKnownUid(mailbox: String): Long {
        return prefs.getLong("${KEY_LAST_KNOWN_UID}_$mailbox", 0L)
    }

    // Compatibility overload
    fun getLastKnownUid(): Long = getLastKnownUid("INBOX/ONCALL")
    fun setLastKnownUid(uid: Long) = setLastKnownUid("INBOX/ONCALL", uid)

    fun setFolderUidValidity(mailbox: String, validity: Long) {
        prefs.edit().putLong("${KEY_UID_VALIDITY}_$mailbox", validity).apply()
    }

    fun getFolderUidValidity(mailbox: String): Long {
        return prefs.getLong("${KEY_UID_VALIDITY}_$mailbox", 0L)
    }

    fun isInitialEnrollmentDone(mailbox: String): Boolean {
        return prefs.getBoolean("${KEY_ENROLLMENT_DONE}_$mailbox", false)
    }

    fun setInitialEnrollmentDone(mailbox: String, done: Boolean) {
        prefs.edit().putBoolean("${KEY_ENROLLMENT_DONE}_$mailbox", done).apply()
    }

    // Health Telemetry
    fun setLastHealthySyncTime(timeMillis: Long) {
        prefs.edit().putLong(KEY_LAST_HEALTHY_SYNC, timeMillis).apply()
    }

    fun getLastHealthySyncTime(): Long {
        return prefs.getLong(KEY_LAST_HEALTHY_SYNC, 0L)
    }

    fun setHealthState(state: HealthState) {
        prefs.edit().putString(KEY_HEALTH_STATE, state.name).apply()
    }

    fun getHealthState(): HealthState {
        val name = prefs.getString(KEY_HEALTH_STATE, HealthState.OFF.name) ?: HealthState.OFF.name
        return try {
            HealthState.valueOf(name)
        } catch (_: Exception) {
            HealthState.OFF
        }
    }

    fun setLastError(error: String?) {
        prefs.edit().putString(KEY_LAST_ERROR, error ?: "").apply()
    }

    fun getLastError(): String? {
        val err = prefs.getString(KEY_LAST_ERROR, null)
        return if (err.isNullOrBlank()) null else err
    }

    fun setReconnectCount(count: Int) {
        prefs.edit().putInt(KEY_RECONNECT_COUNT, count).apply()
    }

    fun getReconnectCount(): Int {
        return prefs.getInt(KEY_RECONNECT_COUNT, 0)
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
        private const val KEY_MAILBOX = "imap_mailbox"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SECURITY_TYPE = "security_type"

        private const val KEY_ON_CALL_ACTIVE = "on_call_active"
        private const val KEY_LAST_KNOWN_UID = "last_known_uid"
        private const val KEY_UID_VALIDITY = "folder_uid_validity"
        private const val KEY_ENROLLMENT_DONE = "initial_enrollment_done"

        private const val KEY_LAST_HEALTHY_SYNC = "last_healthy_sync"
        private const val KEY_HEALTH_STATE = "health_state"
        private const val KEY_LAST_ERROR = "last_error_message"
        private const val KEY_RECONNECT_COUNT = "reconnect_count"

        private const val KEY_RINGTONE_URI = "ringtone_uri"
        private const val KEY_THEME = "app_theme"
        private const val KEY_BATTERY_DISMISSED = "battery_dismissed"
    }
}
