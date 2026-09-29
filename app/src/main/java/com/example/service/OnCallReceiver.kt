package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.AsteintusApp
import com.example.worker.MailSyncWorker

class OnCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(TAG, "Device rebooted. Verifying On-Call standby posture...")
            val app = context.applicationContext as? AsteintusApp ?: return
            val prefs = app.appContainer.encryptedPreferences

            if (prefs.isOnCallActive()) {
                Log.i(TAG, "On-Call standby was active prior to reboot. Resuming AlertService and IMAP IDLE...")
                // Start persistent foreground service & IMAP IDLE
                val serviceIntent = Intent(context, AlertService::class.java).apply {
                    this.action = AlertService.ACTION_START_ON_CALL_NOTIFICATION
                }
                context.startForegroundService(serviceIntent)

                // Schedule watchdog
                MailSyncWorker.startPeriodicSync(context)
            } else {
                Log.d(TAG, "On-call standby inactive. No background service started.")
            }
        }
    }

    companion object {
        private const val TAG = "OnCallReceiver"
    }
}
