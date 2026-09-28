package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.AsteintusApp
import com.example.domain.model.SnoozeOption
import com.example.presentation.alert.AlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveAlertState(
    val alertId: Long = 0L,
    val mailId: Long = 0L,
    val senderName: String = "",
    val senderAddress: String = "",
    val subject: String = "",
    val receivedTime: Long = 0L,
    val isRinging: Boolean = false,
    val isMuted: Boolean = false,
    val queueSize: Int = 0
)

class AlertService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val alertQueue = ArrayDeque<Long>()
    private var currentAlertId: Long = 0L
    private var currentMailId: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initVibrator()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val app = applicationContext as? AsteintusApp ?: return START_NOT_STICKY

        when (action) {
            ACTION_TRIGGER_ALERT -> {
                val alertId = intent.getLongExtra(EXTRA_ALERT_ID, 0L)
                val mailId = intent.getLongExtra(EXTRA_MAIL_ID, 0L)
                if (alertId > 0) {
                    handleTriggerAlert(app, alertId, mailId)
                }
            }
            ACTION_MUTE -> {
                handleMute()
            }
            ACTION_ACKNOWLEDGE -> {
                val alertId = intent.getLongExtra(EXTRA_ALERT_ID, currentAlertId)
                handleAcknowledge(app, alertId)
            }
            ACTION_SNOOZE -> {
                val alertId = intent.getLongExtra(EXTRA_ALERT_ID, currentAlertId)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                val option = when (minutes) {
                    15 -> SnoozeOption.FIFTEEN_MINUTES
                    30 -> SnoozeOption.THIRTY_MINUTES
                    else -> SnoozeOption.FIVE_MINUTES
                }
                handleSnooze(app, alertId, option)
            }
            ACTION_START_ON_CALL_NOTIFICATION -> {
                showOnCallPersistentNotification()
            }
            ACTION_STOP_ON_CALL_NOTIFICATION -> {
                if (currentAlertId == 0L) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP_ALL -> {
                stopAlertPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun handleTriggerAlert(app: AsteintusApp, alertId: Long, mailId: Long) {
        if (currentAlertId == 0L) {
            // First alert to handle
            currentAlertId = alertId
            currentMailId = mailId
            loadAndDisplayAlert(app, alertId, mailId)
        } else if (currentAlertId != alertId && !alertQueue.contains(alertId)) {
            // Add to pending queue
            alertQueue.addLast(alertId)
            updateStateQueueSize()
        }
    }

    private fun loadAndDisplayAlert(app: AsteintusApp, alertId: Long, mailId: Long) {
        serviceScope.launch {
            val alert = app.appContainer.alertRepository.getAlertByIdSync(alertId)
            if (alert == null) {
                advanceQueueOrStop(app)
                return@launch
            }

            currentMailId = alert.mailId
            val notification = buildAlertNotification(alert.id, alert.mailId, alert.senderName, alert.senderAddress, alert.subject)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_ALERT, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID_ALERT, notification)
            }

            startAlertPlayback(app)

            _activeAlertState.value = ActiveAlertState(
                alertId = alert.id,
                mailId = alert.mailId,
                senderName = alert.senderName,
                senderAddress = alert.senderAddress,
                subject = alert.subject,
                receivedTime = alert.receivedTime,
                isRinging = true,
                isMuted = false,
                queueSize = alertQueue.size
            )

            // Launch full-screen alert activity
            val fullScreenIntent = Intent(this@AlertService, AlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AlertActivity.EXTRA_ALERT_ID, alert.id)
                putExtra(AlertActivity.EXTRA_MAIL_ID, alert.mailId)
            }
            startActivity(fullScreenIntent)
        }
    }

    private fun handleMute() {
        Log.d(TAG, "Muting alert ringtone and vibration")
        stopAudioAndVibration()
        _activeAlertState.value = _activeAlertState.value.copy(isRinging = false, isMuted = true)
    }

    private fun handleAcknowledge(app: AsteintusApp, alertId: Long) {
        Log.i(TAG, "Acknowledging alert $alertId")
        stopAudioAndVibration()
        serviceScope.launch {
            app.appContainer.alertRepository.acknowledgeAlert(alertId)
            advanceQueueOrStop(app)
        }
    }

    private fun handleSnooze(app: AsteintusApp, alertId: Long, option: SnoozeOption) {
        Log.i(TAG, "Snoozing alert $alertId for ${option.minutes} min")
        stopAudioAndVibration()
        serviceScope.launch {
            app.appContainer.alertRepository.snoozeAlert(alertId, option)
            advanceQueueOrStop(app)
        }
    }

    private fun advanceQueueOrStop(app: AsteintusApp) {
        if (alertQueue.isNotEmpty()) {
            val nextAlertId = alertQueue.removeFirst()
            currentAlertId = nextAlertId
            loadAndDisplayAlert(app, nextAlertId, 0L)
        } else {
            currentAlertId = 0L
            currentMailId = 0L
            _activeAlertState.value = ActiveAlertState()

            // If on-call is still active, revert to low-priority persistent notification
            if (app.appContainer.encryptedPreferences.isOnCallActive()) {
                showOnCallPersistentNotification()
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startAlertPlayback(app: AsteintusApp) {
        stopAudioAndVibration()

        try {
            // Audio via STREAM_ALARM
            val customUriStr = app.appContainer.encryptedPreferences.getRingtoneUri()
            val ringtoneUri = if (customUriStr.isNotBlank()) {
                Uri.parse(customUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                setDataSource(applicationContext, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start custom MediaPlayer, falling back to default alarm ringtone", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_ALARM)
                            .build()
                    )
                    setDataSource(applicationContext, fallbackUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to initialize fallback MediaPlayer", ex)
            }
        }

        // Start continuous vibration pattern
        try {
            val pattern = longArrayOf(0, 1000, 400, 1000, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger vibration", e)
        }
    }

    private fun stopAudioAndVibration() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun stopAlertPlayback() {
        stopAudioAndVibration()
        currentAlertId = 0L
        currentMailId = 0L
        alertQueue.clear()
        _activeAlertState.value = ActiveAlertState()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Asteintus:AlertWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max safety limit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun buildAlertNotification(
        alertId: Long,
        mailId: Long,
        senderName: String,
        senderAddress: String,
        subject: String
    ): Notification {
        val fullScreenIntent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlertActivity.EXTRA_ALERT_ID, alertId)
            putExtra(AlertActivity.EXTRA_MAIL_ID, mailId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ackIntent = Intent(this, AlertService::class.java).apply {
            action = ACTION_ACKNOWLEDGE
            putExtra(EXTRA_ALERT_ID, alertId)
        }
        val ackPendingIntent = PendingIntent.getService(
            this,
            1002,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, AlertService::class.java).apply {
            action = ACTION_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this,
            1003,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification = NotificationCompat.Builder(
            this,
            AsteintusApp.CHANNEL_ALERT_CRITICAL
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("On-call alert")
            .setContentText("New critical incident received")
            .build()

        return NotificationCompat.Builder(this, AsteintusApp.CHANNEL_ALERT_CRITICAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("CRITICAL ON-CALL ALERT")
            .setContentText("$senderName — $subject")
            .setStyle(NotificationCompat.BigTextStyle().bigText("From: $senderName <$senderAddress>\nSubject: $subject"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ACKNOWLEDGE", ackPendingIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Mute Ringtone", mutePendingIntent)
            .build()
    }

    private fun showOnCallPersistentNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AsteintusApp.CHANNEL_ON_CALL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("On-Call Mode Active")
            .setContentText("Monitoring shared mailbox for emergency alerts.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_ON_CALL, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID_ON_CALL, notification)
        }
    }

    private fun updateStateQueueSize() {
        _activeAlertState.value = _activeAlertState.value.copy(queueSize = alertQueue.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioAndVibration()
        releaseWakeLock()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AlertService"
        const val NOTIFICATION_ID_ALERT = 9001
        const val NOTIFICATION_ID_ON_CALL = 9002

        const val ACTION_TRIGGER_ALERT = "com.example.asteintus.ACTION_TRIGGER_ALERT"
        const val ACTION_MUTE = "com.example.asteintus.ACTION_MUTE"
        const val ACTION_ACKNOWLEDGE = "com.example.asteintus.ACTION_ACKNOWLEDGE"
        const val ACTION_SNOOZE = "com.example.asteintus.ACTION_SNOOZE"
        const val ACTION_START_ON_CALL_NOTIFICATION = "com.example.asteintus.ACTION_START_ON_CALL_NOTIFICATION"
        const val ACTION_STOP_ON_CALL_NOTIFICATION = "com.example.asteintus.ACTION_STOP_ON_CALL_NOTIFICATION"
        const val ACTION_STOP_ALL = "com.example.asteintus.ACTION_STOP_ALL"

        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_MAIL_ID = "extra_mail_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        private val _activeAlertState = MutableStateFlow(ActiveAlertState())
        val activeAlertState: StateFlow<ActiveAlertState> = _activeAlertState.asStateFlow()

        fun isRinging(): Boolean = _activeAlertState.value.isRinging

        fun triggerMute(context: Context) {
            val intent = Intent(context, AlertService::class.java).apply {
                action = ACTION_MUTE
            }
            context.startService(intent)
        }

        fun triggerAcknowledge(context: Context, alertId: Long) {
            val intent = Intent(context, AlertService::class.java).apply {
                action = ACTION_ACKNOWLEDGE
                putExtra(EXTRA_ALERT_ID, alertId)
            }
            context.startService(intent)
        }

        fun triggerSnooze(context: Context, alertId: Long, minutes: Int) {
            val intent = Intent(context, AlertService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ALERT_ID, alertId)
                putExtra(EXTRA_SNOOZE_MINUTES, minutes)
            }
            context.startService(intent)
        }
    }
}
