package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.SnoozeOption
import com.example.presentation.history.AlertHistoryScreen
import com.example.presentation.history.AlertHistoryViewModel
import com.example.presentation.maildetail.MailDetailScreen
import com.example.presentation.maildetail.MailDetailViewModel
import com.example.presentation.maillist.MailListScreen
import com.example.presentation.maillist.MailListViewModel
import com.example.presentation.main.MainScreen
import com.example.presentation.main.MainViewModel
import com.example.presentation.navigation.Screen
import com.example.presentation.settings.AccountConfigScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        val app = application as AsteintusApp
        MainViewModel.provideFactory(
            context = applicationContext,
            prefs = app.appContainer.encryptedPreferences,
            mailRepository = app.appContainer.mailRepository,
            alertRepository = app.appContainer.alertRepository
        )
    }

    private var pendingNavMailId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntentExtras(intent)

        setContent {
            MyApplicationTheme {
                RequestNotificationPermission()
                MainAppNavigation(
                    mainViewModel = mainViewModel,
                    initialMailId = pendingNavMailId,
                    onMailNavigated = { pendingNavMailId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshConfigState()
    }

    private fun handleIntentExtras(intent: Intent?) {
        val mailId = intent?.getLongExtra(EXTRA_NAVIGATE_MAIL_ID, 0L) ?: 0L
        if (mailId > 0) {
            pendingNavMailId = mailId
        }
    }

    companion object {
        const val EXTRA_NAVIGATE_MAIL_ID = "extra_navigate_mail_id"
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Handle result if needed */ }

        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun MainAppNavigation(
    mainViewModel: MainViewModel,
    initialMailId: Long?,
    onMailNavigated: () -> Unit
) {
    val navController = rememberNavController()
    val app = AsteintusApp.instance

    LaunchedEffect(initialMailId) {
        if (initialMailId != null && initialMailId > 0) {
            navController.navigate(Screen.MailDetail.createRoute(initialMailId))
            onMailNavigated()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route
        ) {
            // Main Dashboard
            composable(Screen.Main.route) {
                val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
                val recentMails by mainViewModel.recentMails.collectAsStateWithLifecycle()

                MainScreen(
                    uiState = uiState,
                    recentMails = recentMails,
                    onToggleOnCall = { active -> mainViewModel.toggleOnCallMode(active) },
                    onManualSync = { mainViewModel.triggerManualSync() },
                    onDismissBatteryPrompt = { mainViewModel.dismissBatteryPrompt() },
                    onNavigateToMailList = { navController.navigate(Screen.MailList.route) },
                    onNavigateToMailDetail = { mailId -> navController.navigate(Screen.MailDetail.createRoute(mailId)) },
                    onNavigateToHistory = { navController.navigate(Screen.AlertHistory.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToAccountConfig = { navController.navigate(Screen.AccountConfig.route) }
                )
            }

            // Mailbox Inbox List
            composable(Screen.MailList.route) {
                val mailListViewModel: MailListViewModel = viewModel(
                    factory = MailListViewModel.provideFactory(app.appContainer.mailRepository)
                )
                val mails by mailListViewModel.mails.collectAsStateWithLifecycle()
                val uiState by mailListViewModel.uiState.collectAsStateWithLifecycle()

                MailListScreen(
                    mails = mails,
                    uiState = uiState,
                    onRefresh = { mailListViewModel.refreshMails() },
                    onMailClick = { mailId -> navController.navigate(Screen.MailDetail.createRoute(mailId)) },
                    onNavigateBack = { navController.popBackStack() },
                    onClearError = { mailListViewModel.clearError() }
                )
            }

            // Mail Detail View
            composable(
                route = Screen.MailDetail.route,
                arguments = listOf(navArgument("mailId") { type = NavType.LongType })
            ) { backStackEntry ->
                val mailId = backStackEntry.arguments?.getLong("mailId") ?: 0L
                val mailDetailViewModel: MailDetailViewModel = viewModel(
                    factory = MailDetailViewModel.provideFactory(
                        mailId = mailId,
                        mailRepository = app.appContainer.mailRepository,
                        alertRepository = app.appContainer.alertRepository
                    )
                )
                val uiState by mailDetailViewModel.uiState.collectAsStateWithLifecycle()

                MailDetailScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                    onToggleHtml = { mailDetailViewModel.toggleHtmlMode() },
                    onToggleReadStatus = { mailDetailViewModel.toggleReadStatus() },
                    onAcknowledgeAlert = { alertId -> mailDetailViewModel.acknowledgeAlert(alertId) },
                    onSnoozeAlert = { alertId, option -> mailDetailViewModel.snoozeAlert(alertId, option) }
                )
            }

            // Alert History Screen
            composable(Screen.AlertHistory.route) {
                val historyViewModel: AlertHistoryViewModel = viewModel(
                    factory = AlertHistoryViewModel.provideFactory(app.appContainer.alertRepository)
                )
                val alerts by historyViewModel.alerts.collectAsStateWithLifecycle()

                AlertHistoryScreen(
                    alerts = alerts,
                    onAlertClick = { mailId -> navController.navigate(Screen.MailDetail.createRoute(mailId)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(
                        context = app.applicationContext,
                        prefs = app.appContainer.encryptedPreferences,
                        imapClient = app.appContainer.imapClient,
                        mailRepository = app.appContainer.mailRepository
                    )
                )
                val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                SettingsScreen(
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAccountConfig = { navController.navigate(Screen.AccountConfig.route) },
                    onRingtoneSelected = { uri, title -> settingsViewModel.setRingtoneUri(uri, title) },
                    onTogglePreview = { settingsViewModel.toggleRingtonePreview() },
                    onClearData = { settingsViewModel.clearAllData() }
                )
            }

            // Account Configuration Screen
            composable(Screen.AccountConfig.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(
                        context = app.applicationContext,
                        prefs = app.appContainer.encryptedPreferences,
                        imapClient = app.appContainer.imapClient,
                        mailRepository = app.appContainer.mailRepository
                    )
                )
                val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                AccountConfigScreen(
                    currentConfig = uiState.accountConfig,
                    isTesting = uiState.isTestingConnection,
                    testResult = uiState.connectionTestResult,
                    isTestSuccess = uiState.isTestSuccess,
                    onTestConnection = { config -> settingsViewModel.testConnection(config) },
                    onSaveConfig = { config ->
                        settingsViewModel.saveAccountConfig(config)
                        mainViewModel.refreshConfigState()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
