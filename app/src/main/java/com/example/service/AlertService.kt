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
import com.example.AsteintusApp
import com.example.MainActivity
import com.example.data.mdm.MdmConfigManager
import com.example.domain.model.AlertStatus
import com.example.domain.model.HealthState
import com.example.domain.model.HealthTelemetry
import com.example.domain.model.MailStatus
import com.example.domain.model.SnoozeOption
import com.example.presentation.alert.AlertActivity
import com.example.util.PostureChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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

    private var currentAlertId: Long = 0L
    private var currentMailId: Long = 0L

    private var idleJob: Job? = null
    private var watchdogJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initVibrator()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? AsteintusApp ?: return START_NOT_STICKY
        val action = intent?.action ?: ACTION_START_ON_CALL_NOTIFICATION

        when (action) {
            ACTION_TRIGGER_ALERT -> {
                val alertId = intent?.getLongExtra(EXTRA_ALERT_ID, 0L) ?: 0L
                val mailId = intent?.getLongExtra(EXTRA_MAIL_ID, 0L) ?: 0L
                if (alertId > 0) {
                    handleTriggerAlert(app, alertId, mailId)
                } else {
                    checkAndResumeActiveAlerts(app)
                }
            }
            ACTION_MUTE -> {
                handleMute(app)
            }
            ACTION_ACKNOWLEDGE -> {
                val alertId = intent?.getLongExtra(EXTRA_ALERT_ID, currentAlertId) ?: currentAlertId
                handleAcknowledge(app, alertId)
            }
            ACTION_SNOOZE -> {
                val alertId = intent?.getLongExtra(EXTRA_ALERT_ID, currentAlertId) ?: currentAlertId
                val minutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 5) ?: 5
                val option = when (minutes) {
                    15 -> SnoozeOption.FIFTEEN_MINUTES
                    30 -> SnoozeOption.THIRTY_MINUTES
                    else -> SnoozeOption.FIVE_MINUTES
                }
                handleSnooze(app, alertId, option)
            }
            ACTION_START_ON_CALL_NOTIFICATION -> {
                startOnCallIdleAndWatchdog(app)
            }
            ACTION_STOP_ON_CALL_NOTIFICATION -> {
                stopIdleLoop()
                if (currentAlertId == 0L) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP_ALL -> {
                stopIdleLoop()
                stopAlertPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startOnCallIdleAndWatchdog(app: AsteintusApp) {
        val prefs = app.appContainer.encryptedPreferences
        val config = prefs.getAccountConfig()

        // Check posture first
        val posture = PostureChecker.checkPosture(this)
        if (!posture.isNotificationsEnabled) {
            updateHealth(HealthState.BLOCKED, "Notifications désactivées au niveau du système.")
            showOnCallPersistentNotification()
            return
        }

        if (!config.isConfigured) {
            updateHealth(HealthState.BLOCKED, "Compte IMAP non configuré.")
            showOnCallPersistentNotification()
            return
        }

        showOnCallPersistentNotification()
        checkAndResumeActiveAlerts(app)

        // Launch watchdog job if not running
        if (watchdogJob?.isActive != true) {
            watchdogJob = serviceScope.launch {
                while (isActive) {
                    delay(5000L)
                    evaluateSlaHealth(app)
                }
            }
        }

        // Launch IMAP IDLE job if not running
        if (idleJob?.isActive != true) {
            idleJob = serviceScope.launch(Dispatchers.IO) {
                runIdleEngine(app)
            }
        }
    }

    private suspend fun runIdleEngine(app: AsteintusApp) {
        val prefs = app.appContainer.encryptedPreferences
        val imapClient = app.appContainer.imapClient
        val alertRepo = app.appContainer.alertRepository
        val mailDao = app.appContainer.appDatabase.mailDao()

        var backoffMs = 5000L

        while (serviceScope.isActive && prefs.isOnCallActive()) {
            val config = prefs.getAccountConfig()
            val mailbox = config.mailbox.ifBlank { "INBOX/ONCALL" }
            val policy = MdmConfigManager.getMdmPolicy(this@AlertService)

            try {
                Log.i(TAG, "Starting IMAP IDLE connection to ${config.imapHost}:$mailbox...")
                updateHealth(HealthState.READY, null, isIdleActive = true)

                imapClient.runIdleLoop(
                    config = config,
                    mailbox = mailbox,
                    onNewMessageReceived = {
                        processIncomingMessages(app, config, mailbox, policy)
                    },
                    onHeartbeatHealthy = {
                        prefs.setLastHealthySyncTime(System.currentTimeMillis())
                        updateHealth(HealthState.READY, null, isIdleActive = true)
                    }
                )

                // If exited cleanly without exception, reset backoff
                backoffMs = 5000L

            } catch (e: Exception) {
                Log.e(TAG, "IMAP IDLE connection lost, will reconnect in ${backoffMs}ms", e)
                val count = prefs.getReconnectCount() + 1
                prefs.setReconnectCount(count)
                prefs.setLastError(e.localizedMessage ?: "Erreur de connexion IMAP")

                updateHealth(HealthState.DEGRADED, e.localizedMessage, isIdleActive = false)
                showOnCallPersistentNotification()

                delay(backoffMs)
                backoffMs = minOf(backoffMs * 2, 60000L) // Bounded backoff up to 60s
            }
        }
    }

    private suspend fun processIncomingMessages(
        app: AsteintusApp,
        config: com.example.data.prefs.AccountConfig,
        mailbox: String,
        policy: com.example.domain.model.AlertPolicy
    ) {
        val prefs = app.appContainer.encryptedPreferences
        val mailDao = app.appContainer.appDatabase.mailDao()
        val alertRepo = app.appContainer.alertRepository
        val imapClient = app.appContainer.imapClient

        val sinceUid = prefs.getLastKnownUid(mailbox)
        val savedValidity = prefs.getFolderUidValidity(mailbox)

        val fetchResult = imapClient.fetchNewMessages(config, mailbox, sinceUid, savedValidity)
        if (fetchResult.isFailure) {
            Log.e(TAG, "Failed to fetch messages: ${fetchResult.exceptionOrNull()?.message}")
            return
        }

        val result = fetchResult.getOrThrow()
        prefs.setFolderUidValidity(mailbox, result.folderUidValidity)
        prefs.setLastHealthySyncTime(System.currentTimeMillis())

        if (result.isFirstEnrollment) {
            prefs.setLastKnownUid(mailbox, result.latestUid)
            prefs.setInitialEnrollmentDone(mailbox, true)
            Log.i(TAG, "Silent initial enrollment completed for $mailbox at UID ${result.latestUid}")
            return
        }

        var maxUid = sinceUid

        for (msg in result.messages) {
            if (msg.uid > maxUid) maxUid = msg.uid

            // Evaluate against AlertPolicy
            val eligibility = policy.isEligible(
                folderName = mailbox,
                senderAddress = msg.senderAddress,
                subject = msg.subject,
                headers = msg.headers,
                uid = msg.uid,
                uidValidity = result.folderUidValidity
            )

            if (!eligibility.isEligible) {
                Log.d(TAG, "Mail UID ${msg.uid} rejected by AlertPolicy: ${eligibility.reason}")
                continue
            }

            // Save eligible email in DB
            val existing = mailDao.getMailByUidAndValidity(msg.uid, result.folderUidValidity)
            val mailId = if (existing != null) {
                existing.id
            } else {
                mailDao.insertMail(
                    com.example.data.local.entity.MailMessageEntity(
                        uid = msg.uid,
                        uidValidity = result.folderUidValidity,
                        mailbox = mailbox,
                        senderName = msg.senderName,
                        senderAddress = msg.senderAddress,
                        subject = msg.subject,
                        receivedDate = msg.receivedDate,
                        bodyExcerpt = msg.bodyExcerpt,
                        bodyText = msg.bodyText,
                        bodyHtml = msg.bodyHtml,
                        status = MailStatus.NEW.name,
                        incidentId = eligibility.incidentId,
                        policyVersion = policy.version
                    )
                )
            }

            // Trigger Alert in DB with deduplication key
            val alertResult = alertRepo.triggerAlertForMail(
                mailId = mailId,
                dedupKey = eligibility.dedupKey,
                reason = eligibility.reason
            )

            if (alertResult.isSuccess) {
                val alert = alertResult.getOrThrow()
                Log.i(TAG, "Triggering emergency alarm for alert #${alert.id} (${alert.subject})")
                handleTriggerAlert(app, alert.id, mailId)
            }
        }

        if (maxUid > sinceUid) {
            prefs.setLastKnownUid(mailbox, maxUid)
        }
    }

    private fun evaluateSlaHealth(app: AsteintusApp) {
        val prefs = app.appContainer.encryptedPreferences
        if (!prefs.isOnCallActive()) {
            updateHealth(HealthState.OFF, null)
            return
        }

        val lastSync = prefs.getLastHealthySyncTime()
        val elapsedSeconds = if (lastSync > 0) (System.currentTimeMillis() - lastSync) / 1000L else 9999L

        if (elapsedSeconds > 180L) {
            // SLA compromised!
            updateHealth(
                HealthState.ESCALATING,
                "SLA compromis (> 3 min sans synchronisation saine). Canal secondaire requis !"
            )
            showOnCallPersistentNotification()
        } else if (elapsedSeconds > 90L && _healthTelemetry.value.state == HealthState.READY) {
            updateHealth(HealthState.DEGRADED, "Latence de synchronisation élevée (> 90s).")
            showOnCallPersistentNotification()
        }
    }

    private fun updateHealth(state: HealthState, errorMessage: String?, isIdleActive: Boolean = false) {
        val app = applicationContext as? AsteintusApp ?: return
        val prefs = app.appContainer.encryptedPreferences
        prefs.setHealthState(state)
        if (errorMessage != null) prefs.setLastError(errorMessage)

        val config = prefs.getAccountConfig()
        val policy = MdmConfigManager.getMdmPolicy(this)

        _healthTelemetry.value = HealthTelemetry(
            state = state,
            mailbox = config.mailbox,
            isIdleActive = isIdleActive,
            lastHealthySyncTimestamp = prefs.getLastHealthySyncTime(),
            reconnectCount = prefs.getReconnectCount(),
            lastErrorMessage = errorMessage ?: prefs.getLastError(),
            lastProcessedUid = prefs.getLastKnownUid(config.mailbox),
            folderUidValidity = prefs.getFolderUidValidity(config.mailbox),
            policyVersion = policy.version,
            isMdmManaged = policy.isMdmManaged
        )
    }

    private fun stopIdleLoop() {
        idleJob?.cancel()
        idleJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        updateHealth(HealthState.OFF, null)
    }

    /**
     * DB is the single source of truth for active alerts.
     */
    private fun checkAndResumeActiveAlerts(app: AsteintusApp) {
        serviceScope.launch {
            val alertRepo = app.appContainer.alertRepository
            alertRepo.checkAndReactivateSnoozes()
            val activeAlerts = alertRepo.getActiveAlertsSync()

            if (activeAlerts.isNotEmpty()) {
                val candidate = activeAlerts.firstOrNull { it.status == AlertStatus.RINGING || it.status == AlertStatus.PENDING }
                    ?: activeAlerts.first()

                currentAlertId = candidate.id
                currentMailId = candidate.mailId
                loadAndDisplayAlert(app, candidate.id, candidate.mailId)
            }
        }
    }

    private fun handleTriggerAlert(app: AsteintusApp, alertId: Long, mailId: Long) {
        if (currentAlertId == 0L || currentAlertId == alertId) {
            currentAlertId = alertId
            currentMailId = mailId
            loadAndDisplayAlert(app, alertId, mailId)
        } else {
            // Already ringing an alert; DB holds the rest as single source of truth
            updateStateQueueSize(app)
        }
    }

    private fun loadAndDisplayAlert(app: AsteintusApp, alertId: Long, mailId: Long) {
        serviceScope.launch {
            val alert = app.appContainer.alertRepository.getAlertByIdSync(alertId)
            if (alert == null || alert.status == AlertStatus.ACKNOWLEDGED) {
                advanceQueueOrStop(app)
                return@launch
            }

            currentAlertId = alert.id
            currentMailId = alert.mailId

            val notification = buildAlertNotification(alert.id, alert.mailId, alert.senderName, alert.senderAddress, alert.subject)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_ALERT, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID_ALERT, notification)
            }

            startAlertPlayback(app)

            val activeCount = app.appContainer.alertRepository.getActiveAlertsSync().size

            _activeAlertState.value = ActiveAlertState(
                alertId = alert.id,
                mailId = alert.mailId,
                senderName = alert.senderName,
                senderAddress = alert.senderAddress,
                subject = alert.subject,
                receivedTime = alert.receivedTime,
                isRinging = true,
                isMuted = false,
                queueSize = maxOf(0, activeCount - 1)
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

    private fun handleMute(app: AsteintusApp) {
        Log.d(TAG, "Muting alert ringtone and vibration")
        stopAudioAndVibration()
        _activeAlertState.value = _activeAlertState.value.copy(isRinging = false, isMuted = true)
        if (currentAlertId > 0) {
            serviceScope.launch {
                app.appContainer.alertRepository.muteAlert(currentAlertId)
            }
        }
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
        serviceScope.launch {
            val remainingAlerts = app.appContainer.alertRepository.getActiveAlertsSync()
                .filter { it.status == AlertStatus.PENDING || it.status == AlertStatus.RINGING }

            if (remainingAlerts.isNotEmpty()) {
                val next = remainingAlerts.first()
                currentAlertId = next.id
                currentMailId = next.mailId
                loadAndDisplayAlert(app, next.id, next.mailId)
            } else {
                currentAlertId = 0L
                currentMailId = 0L
                _activeAlertState.value = ActiveAlertState()

                if (app.appContainer.encryptedPreferences.isOnCallActive()) {
                    showOnCallPersistentNotification()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun updateStateQueueSize(app: AsteintusApp) {
        serviceScope.launch {
            val totalActive = app.appContainer.alertRepository.getActiveAlertsSync().size
            _activeAlertState.value = _activeAlertState.value.copy(queueSize = maxOf(0, totalActive - 1))
        }
    }

    private fun startAlertPlayback(app: AsteintusApp) {
        stopAudioAndVibration()

        try {
            // Audio via STREAM_ALARM (DND bypass)
            val ringtoneUriStr = app.appContainer.encryptedPreferences.getRingtoneUri()
            val alertUri = if (ringtoneUriStr.isNotBlank()) {
                Uri.parse(ringtoneUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlertService, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaPlayer for alarm", e)
        }

        try {
            // Haptic vibration
            val pattern = longArrayOf(0, 1000, 400, 1000, 400, 1500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Vibrator", e)
        }
    }

    private fun stopAudioAndVibration() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaPlayer", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error canceling Vibrator", e)
        }
    }

    private fun stopAlertPlayback() {
        stopAudioAndVibration()
        currentAlertId = 0L
        currentMailId = 0L
        _activeAlertState.value = ActiveAlertState()
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
            alertId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, AlertService::class.java).apply {
            action = ACTION_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this,
            1001,
            muteIntent,
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

        val snoozeIntent = Intent(this, AlertService::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_SNOOZE_MINUTES, 5)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            1003,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AsteintusApp.CHANNEL_ALERT_CRITICAL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 ALERTE ASTREINTE : $subject")
            .setContentText("De : ${senderName.ifBlank { senderAddress }}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("De : $senderName <$senderAddress>\n\n$subject"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Mute", mutePendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 5m", snoozePendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Acquitter", ackPendingIntent)
            .build()
    }

    private fun showOnCallPersistentNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            2001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val telemetry = _healthTelemetry.value
        val (title, text) = when (telemetry.state) {
            HealthState.READY -> Pair(
                "Asteintus : Astreinte Active (SLA < 3m)",
                "Surveillance IMAP IDLE active sur ${telemetry.mailbox} • Conforme"
            )
            HealthState.DEGRADED -> Pair(
                "Asteintus : Synchronisation Dégradée",
                "Reconnexion IMAP en cours... (${telemetry.lastErrorMessage ?: "Attente réseau"})"
            )
            HealthState.BLOCKED -> Pair(
                "Asteintus : Astreinte Bloquée",
                telemetry.lastErrorMessage ?: "Configuration requise"
            )
            HealthState.ESCALATING -> Pair(
                "⚠️ ASTEINTUS : SLA COMPROMIS (> 3 min)",
                "L'alerte par email ne peut être garantie. Activer le canal secondaire !"
            )
            HealthState.OFF -> Pair(
                "Asteintus : Inactif",
                "Mode astreinte coupé"
            )
        }

        val notification = NotificationCompat.Builder(this, AsteintusApp.CHANNEL_ON_CALL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(if (telemetry.state == HealthState.ESCALATING) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()

        startForeground(NOTIFICATION_ID_ON_CALL, notification)
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Asteintus:AlertServiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
    }

    override fun onDestroy() {
        stopIdleLoop()
        stopAudioAndVibration()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlertService"

        const val NOTIFICATION_ID_ALERT = 9001
        const val NOTIFICATION_ID_ON_CALL = 9002

        const val ACTION_TRIGGER_ALERT = "com.example.service.ACTION_TRIGGER_ALERT"
        const val ACTION_MUTE = "com.example.service.ACTION_MUTE"
        const val ACTION_ACKNOWLEDGE = "com.example.service.ACTION_ACKNOWLEDGE"
        const val ACTION_SNOOZE = "com.example.service.ACTION_SNOOZE"
        const val ACTION_START_ON_CALL_NOTIFICATION = "com.example.service.ACTION_START_ON_CALL_NOTIFICATION"
        const val ACTION_STOP_ON_CALL_NOTIFICATION = "com.example.service.ACTION_STOP_ON_CALL_NOTIFICATION"
        const val ACTION_STOP_ALL = "com.example.service.ACTION_STOP_ALL"

        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_MAIL_ID = "extra_mail_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        private val _activeAlertState = MutableStateFlow(ActiveAlertState())
        val activeAlertState: StateFlow<ActiveAlertState> = _activeAlertState.asStateFlow()

        private val _healthTelemetry = MutableStateFlow(HealthTelemetry())
        val healthTelemetryFlow: StateFlow<HealthTelemetry> = _healthTelemetry.asStateFlow()

        fun isRinging(): Boolean = _activeAlertState.value.isRinging

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

        fun triggerMute(context: Context) {
            val intent = Intent(context, AlertService::class.java).apply {
                action = ACTION_MUTE
            }
            context.startService(intent)
        }
    }
}
