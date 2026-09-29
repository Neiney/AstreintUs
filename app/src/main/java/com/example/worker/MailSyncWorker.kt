package com.example.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.AsteintusApp
import com.example.service.AlertService
import java.util.concurrent.TimeUnit

/**
 * MailSyncWorker acts strictly as a watchdog, safety net, and health auditor.
 * It is NOT the primary low-latency mechanism (which is IMAP IDLE in AlertService).
 */
class MailSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AsteintusApp ?: return Result.failure()
        val prefs = app.appContainer.encryptedPreferences
        val mailRepository = app.appContainer.mailRepository
        val alertRepository = app.appContainer.alertRepository

        if (!prefs.isOnCallActive()) {
            Log.d(TAG, "On-call mode is inactive. Watchdog skipping.")
            return Result.success()
        }

        val config = prefs.getAccountConfig()
        if (!config.isConfigured) {
            Log.w(TAG, "Account is not configured. Watchdog skipping.")
            return Result.success()
        }

        try {
            Log.d(TAG, "Watchdog execution: checking health and reactive fallback...")

            // 1. Reactivate expired snoozes
            val reactivatedCount = alertRepository.checkAndReactivateSnoozes()
            if (reactivatedCount > 0) {
                Log.d(TAG, "Watchdog reactivated $reactivatedCount expired snoozes.")
            }

            // 2. Verify if AlertService is running and healthy
            val lastSync = prefs.getLastHealthySyncTime()
            val syncAgeSeconds = if (lastSync > 0) (System.currentTimeMillis() - lastSync) / 1000L else 9999L

            if (syncAgeSeconds > 120L) {
                Log.w(TAG, "IMAP IDLE sync age is ${syncAgeSeconds}s (> 120s). Executing fallback sync.")
                val fetchResult = mailRepository.fetchAndSaveNewMails()
                if (fetchResult.isSuccess) {
                    val newMails = fetchResult.getOrDefault(emptyList())
                    for (mail in newMails) {
                        alertRepository.triggerAlertForMail(mail.id)
                    }
                }

                // Ensure foreground service is running
                val serviceIntent = Intent(context, AlertService::class.java).apply {
                    action = AlertService.ACTION_START_ON_CALL_NOTIFICATION
                }
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "IMAP IDLE is healthy (last sync ${syncAgeSeconds}s ago). No fallback sync required.")
            }

            // 3. Check for any unhandled pending alerts that need attention
            val pendingAlerts = alertRepository.getPendingAlertsSync()
            if (pendingAlerts.isNotEmpty() && !AlertService.isRinging()) {
                val firstPending = pendingAlerts.first()
                val alertIntent = Intent(context, AlertService::class.java).apply {
                    action = AlertService.ACTION_TRIGGER_ALERT
                    putExtra(AlertService.EXTRA_ALERT_ID, firstPending.id)
                    putExtra(AlertService.EXTRA_MAIL_ID, firstPending.mailId)
                }
                context.startForegroundService(alertIntent)
            }

            // 4. GDPR Art. 5 Data Retention policy purge (30 days)
            enforceDataRetention(app.appContainer.appDatabase)

        } catch (e: Exception) {
            Log.e(TAG, "Error in Watchdog Worker execution", e)
        } finally {
            if (prefs.isOnCallActive()) {
                scheduleNextWatchdog(context)
            }
        }

        return Result.success()
    }

    private suspend fun enforceDataRetention(database: com.example.data.local.db.AppDatabase) {
        val retentionMillis = com.example.AppConstants.DATA_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        val threshold = System.currentTimeMillis() - retentionMillis
        val deletedMails = database.mailDao().deleteMailsOlderThan(threshold)
        val deletedAlerts = database.alertDao().deleteAlertsOlderThan(threshold)
        if (deletedMails > 0 || deletedAlerts > 0) {
            Log.i("DataRetention", "GDPR Purge: deleted $deletedMails mails and $deletedAlerts alerts older than 30 days.")
        }
    }

    companion object {
        private const val TAG = "MailSyncWatchdog"
        const val WORK_NAME = "AsteintusWatchdogWork"

        fun startPeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleNextWatchdog(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
