package com.example.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.AsteintusApp
import com.example.domain.model.MailStatus
import com.example.service.AlertService
import java.util.concurrent.TimeUnit

class MailSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AsteintusApp ?: return Result.failure()
        val prefs = app.appContainer.encryptedPreferences
        val mailRepository = app.appContainer.mailRepository
        val alertRepository = app.appContainer.alertRepository

        // Outside of on-call mode, synchronisation is suspended
        if (!prefs.isOnCallActive()) {
            Log.d(TAG, "On-call mode is inactive. Skipping sync.")
            return Result.success()
        }

        val config = prefs.getAccountConfig()
        if (!config.isConfigured) {
            Log.w(TAG, "Account is not configured. Skipping sync.")
            return Result.success()
        }

        try {
            Log.d(TAG, "Starting IMAP mail synchronization...")

            // Check and reactivate any snoozed alerts that expired
            val reactivatedCount = alertRepository.checkAndReactivateSnoozes()
            if (reactivatedCount > 0) {
                Log.d(TAG, "Reactivated $reactivatedCount expired snoozed alerts.")
            }

            // Fetch and save new mails
            val fetchResult = mailRepository.fetchAndSaveNewMails()
            if (fetchResult.isSuccess) {
                val newMails = fetchResult.getOrDefault(emptyList())
                Log.d(TAG, "Fetched ${newMails.size} new emails.")

                for (mail in newMails) {
                    val alertResult = alertRepository.triggerAlertForMail(mail.id)
                    if (alertResult.isSuccess) {
                        val alert = alertResult.getOrThrow()
                        Log.i(TAG, "Triggering critical alert for mail UID ${mail.uid} (Alert #${alert.id})")
                        // Trigger alert service
                        val alertIntent = Intent(context, AlertService::class.java).apply {
                            action = AlertService.ACTION_TRIGGER_ALERT
                            putExtra(AlertService.EXTRA_ALERT_ID, alert.id)
                            putExtra(AlertService.EXTRA_MAIL_ID, mail.id)
                        }
                        context.startForegroundService(alertIntent)
                    }
                }
            } else {
                Log.e(TAG, "Failed to fetch mails: ${fetchResult.exceptionOrNull()?.message}")
            }

            // Check if there are any unhandled pending alerts that need ringing
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

            // Enforce GDPR Art. 5 Data Retention policy at the end of sync cycle
            enforceDataRetention(app.appContainer.appDatabase)

        } catch (e: Exception) {
            Log.e(TAG, "Error in MailSyncWorker execution", e)
        } finally {
            // If on-call mode is still active, schedule the next sync in 60 seconds
            if (prefs.isOnCallActive()) {
                scheduleNextSync(context)
            }
        }

        return Result.success()
    }

    private suspend fun enforceDataRetention(database: com.example.data.local.db.AppDatabase) {
        val retentionMillis = com.example.AppConstants.DATA_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        val threshold = System.currentTimeMillis() - retentionMillis
        val deletedMails = database.mailDao().deleteMailsOlderThan(threshold)
        val deletedAlerts = database.alertDao().deleteAlertsOlderThan(threshold)
        Log.i("DataRetention", "Purged $deletedMails mails and $deletedAlerts alerts older than 30 days.")
    }

    companion object {
        private const val TAG = "MailSyncWorker"
        const val WORK_NAME = "AsteintusSyncWork"

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

        fun scheduleNextSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(60, TimeUnit.SECONDS)
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
