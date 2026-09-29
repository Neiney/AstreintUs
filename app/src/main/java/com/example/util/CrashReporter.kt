package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.presentation.crash.CrashReportActivity
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashInfo(
    val timestamp: String,
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String,
    val deviceInfo: String
)

object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val PREFS_NAME = "asteintus_crash_log_secure"
    private const val KEY_TIMESTAMP_MILLIS = "crash_timestamp_millis"
    private const val KEY_TIMESTAMP = "crash_timestamp"
    private const val KEY_ERROR_TYPE = "crash_error_type"
    private const val KEY_ERROR_MESSAGE = "crash_error_message"
    private const val KEY_STACK_TRACE = "crash_stack_trace"
    private const val KEY_DEVICE_INFO = "crash_device_info"

    // Short retention policy: 7 days
    private const val MAX_CRASH_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000L

    @Volatile
    private var cachedCrash: CrashInfo? = null

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Fatal uncaught crash on thread ${thread.name}", throwable)
            try {
                recordException(context, throwable)
                launchCrashScreen(context, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch crash screen", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordException(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val rawStackTrace = sw.toString()

            // Lot 1 Step 3: Sanitize traces to remove secrets, passwords, emails and body text
            val sanitizedTrace = sanitizeTrace(rawStackTrace)
            val sanitizedMessage = sanitizeTrace(throwable.message ?: "Unknown error")

            val nowMillis = System.currentTimeMillis()
            val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(nowMillis))
            val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val info = CrashInfo(
                timestamp = nowFormatted,
                errorType = throwable.javaClass.simpleName,
                errorMessage = sanitizedMessage,
                stackTrace = sanitizedTrace,
                deviceInfo = deviceInfo
            )
            cachedCrash = info

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_TIMESTAMP_MILLIS, nowMillis)
                .putString(KEY_TIMESTAMP, nowFormatted)
                .putString(KEY_ERROR_TYPE, info.errorType)
                .putString(KEY_ERROR_MESSAGE, info.errorMessage)
                .putString(KEY_STACK_TRACE, info.stackTrace)
                .putString(KEY_DEVICE_INFO, info.deviceInfo)
                .commit()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sanitized crash log", e)
        }
    }

    fun getLastCrash(context: Context): CrashInfo? {
        if (cachedCrash != null) return cachedCrash

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestampMillis = prefs.getLong(KEY_TIMESTAMP_MILLIS, 0L)
        if (timestampMillis == 0L || (System.currentTimeMillis() - timestampMillis > MAX_CRASH_RETENTION_MILLIS)) {
            // Expired or absent
            clearCrash(context)
            return null
        }

        val timestamp = prefs.getString(KEY_TIMESTAMP, null) ?: return null
        val info = CrashInfo(
            timestamp = timestamp,
            errorType = prefs.getString(KEY_ERROR_TYPE, "Unknown") ?: "Unknown",
            errorMessage = prefs.getString(KEY_ERROR_MESSAGE, "No message") ?: "No message",
            stackTrace = prefs.getString(KEY_STACK_TRACE, "") ?: "",
            deviceInfo = prefs.getString(KEY_DEVICE_INFO, "") ?: ""
        )
        cachedCrash = info
        return info
    }

    fun clearCrash(context: Context) {
        cachedCrash = null
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
    }

    fun launchCrashScreen(context: Context, throwable: Throwable) {
        // Do not transport full raw stack trace via Intent to avoid IPC payload leaks
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(CrashReportActivity.EXTRA_ERROR_TYPE, throwable.javaClass.simpleName)
            putExtra(CrashReportActivity.EXTRA_ERROR_MSG, sanitizeTrace(throwable.message ?: "Unknown error"))
        }
        context.startActivity(intent)
    }

    private fun sanitizeTrace(input: String): String {
        return input
            // Redact passwords, tokens, keys
            .replace(Regex("(?i)(password|passphrase|secret|token|bearer|key)\\s*[:=]\\s*[^\\s,;\"']+", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            // Redact email addresses to anonymized domain
            .replace(Regex("[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"), "[user]@$1")
    }
}
