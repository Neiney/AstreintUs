package com.example.presentation.alert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.AsteintusApp
import com.example.service.AlertService
import com.example.ui.theme.MyApplicationTheme

class AlertActivity : ComponentActivity() {

    private val viewModel: AlertViewModel by viewModels {
        val app = application as AsteintusApp
        AlertViewModel.provideFactory(app.appContainer.alertRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenFlags()

        val alertId = intent.getLongExtra(EXTRA_ALERT_ID, 0L)
        if (alertId > 0) {
            viewModel.loadAlert(alertId)
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                AlertScreen(
                    uiState = uiState,
                    onAcknowledge = { id ->
                        AlertService.triggerAcknowledge(this, id)
                        finish()
                    },
                    onSnooze = { id, minutes ->
                        AlertService.triggerSnooze(this, id, minutes)
                        finish()
                    },
                    onOpenMail = { mailId ->
                        // Open mail detail in MainActivity without acknowledging (ringtone continues)
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(MainActivity.EXTRA_NAVIGATE_MAIL_ID, mailId)
                        }
                        startActivity(mainIntent)
                    },
                    onMuteRingtone = {
                        AlertService.triggerMute(this)
                    }
                )
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Physical volume button cuts the ringtone only. Vibration stops. The alert remains visually active.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            AlertService.triggerMute(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_MAIL_ID = "mail_id"
    }
}
