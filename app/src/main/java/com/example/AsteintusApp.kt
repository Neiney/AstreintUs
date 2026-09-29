package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

class AsteintusApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    private val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED) {
                Log.i("AsteintusApp", "MDM Application Restrictions changed by enterprise policy.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.example.util.CrashReporter.init(this)

        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Throwable) {
            Log.w("AsteintusApp", "SQLCipher native library notice: ${e.message}")
        }

        instance = this
        appContainer = AppContainer(this)
        createNotificationChannels()

        // Register receiver for MDM restrictions change
        try {
            val filter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(restrictionsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(restrictionsReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w("AsteintusApp", "Failed to register MDM restrictions receiver", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Critical alert channel with max importance and alarm sound attributes
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_CRITICAL,
                "On-Call Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency ringtone and full-screen alerts for on-call personnel"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 400, 1000, 400)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // Persistent on-call status channel
            val onCallStatusChannel = NotificationChannel(
                CHANNEL_ON_CALL_STATUS,
                "On-Call Active Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent icon indicating on-call standby is active"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(onCallStatusChannel)
        }
    }

    companion object {
        const val CHANNEL_ALERT_CRITICAL = "channel_oncall_critical_alert"
        const val CHANNEL_ON_CALL_STATUS = "channel_oncall_status"

        lateinit var instance: AsteintusApp
            private set
    }
}
