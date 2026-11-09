package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.presentation.crash.CrashReportActivity

data class CrashInfo(
    val timestamp: String,
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String,
    val deviceInfo: String
)

object CrashReporter {

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
        Log.e("CrashReporter", "Fatal crash captured without persisting sensitive diagnostics", throwable)
    }

    fun getLastCrash(context: Context): CrashInfo? = null

    fun clearCrash(context: Context) = Unit

    fun launchCrashScreen(context: Context, throwable: Throwable) {
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(CrashReportActivity.EXTRA_ERROR_TYPE, throwable.javaClass.name)
            putExtra(CrashReportActivity.EXTRA_ERROR_MSG, throwable.message ?: "Unknown error")
            putExtra(CrashReportActivity.EXTRA_STACK_TRACE, "Diagnostics are available through managed device logs.")
        }
        context.startActivity(intent)
    }
}
