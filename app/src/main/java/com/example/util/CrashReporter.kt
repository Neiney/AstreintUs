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

    private const val PREFS_NAME = "asteintus_crash_log"
    private const val KEY_TIMESTAMP = "crash_timestamp"
    private const val KEY_ERROR_TYPE = "crash_error_type"
    private const val KEY_ERROR_MESSAGE = "crash_error_message"
    private const val KEY_STACK_TRACE = "crash_stack_trace"
    private const val KEY_DEVICE_INFO = "crash_device_info"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CrashReporter", "Fatal uncaught crash on thread ${thread.name}", throwable)
            try {
                recordException(context, throwable)
                launchCrashScreen(context, throwable)
            } catch (e: Exception) {
                Log.e("CrashReporter", "Failed to launch crash screen", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordException(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}"

            prefs.edit()
                .putString(KEY_TIMESTAMP, now)
                .putString(KEY_ERROR_TYPE, throwable.javaClass.name)
                .putString(KEY_ERROR_MESSAGE, throwable.message ?: "Unknown error")
                .putString(KEY_STACK_TRACE, stackTrace)
                .putString(KEY_DEVICE_INFO, deviceInfo)
                .commit()
        } catch (e: Exception) {
            Log.e("CrashReporter", "Failed to save crash log", e)
        }
    }

    fun getLastCrash(context: Context): CrashInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getString(KEY_TIMESTAMP, null) ?: return null
        return CrashInfo(
            timestamp = timestamp,
            errorType = prefs.getString(KEY_ERROR_TYPE, "Unknown") ?: "Unknown",
            errorMessage = prefs.getString(KEY_ERROR_MESSAGE, "No message") ?: "No message",
            stackTrace = prefs.getString(KEY_STACK_TRACE, "") ?: "",
            deviceInfo = prefs.getString(KEY_DEVICE_INFO, "") ?: ""
        )
    }

    fun clearCrash(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun launchCrashScreen(context: Context, throwable: Throwable) {
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(CrashReportActivity.EXTRA_ERROR_TYPE, throwable.javaClass.name)
            putExtra(CrashReportActivity.EXTRA_ERROR_MSG, throwable.message ?: "Unknown error")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            putExtra(CrashReportActivity.EXTRA_STACK_TRACE, sw.toString())
        }
        context.startActivity(intent)
    }
}
