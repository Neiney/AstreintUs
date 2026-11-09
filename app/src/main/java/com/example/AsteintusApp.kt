package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

class AsteintusApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.example.util.CrashReporter.init(this)
        instance = this
        appContainer = AppContainer(this)
        createNotificationChannels()
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

            // Low importance persistent on-call status channel
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
