package com.example.presentation.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Mail
import com.example.domain.model.MailStatus
import com.example.presentation.alert.AlertActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    recentMails: List<Mail>,
    onToggleOnCall: (Boolean) -> Unit,
    onManualSync: () -> Unit,
    onDismissBatteryPrompt: () -> Unit,
    onNavigateToMailList: () -> Unit,
    onNavigateToMailDetail: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccountConfig: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isOnCallActive) Color(0xFF22C55E) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Asteintus",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onManualSync,
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.testTag("manual_sync_button")
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Mailbox")
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("main_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Active Alert Emergency Banner (if an alert is actively ringing)
            if (uiState.activeAlert.alertId > 0) {
                item {
                    ActiveAlertBanner(
                        activeAlert = uiState.activeAlert,
                        onOpenAlert = {
                            val alertIntent = Intent(context, AlertActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(AlertActivity.EXTRA_ALERT_ID, uiState.activeAlert.alertId)
                                putExtra(AlertActivity.EXTRA_MAIL_ID, uiState.activeAlert.mailId)
                            }
                            context.startActivity(alertIntent)
                        }
                    )
                }
            }

            // On-Call Mode Toggle Card (Core Feature)
            item {
                OnCallToggleCard(
                    isActive = uiState.isOnCallActive,
                    isConfigured = uiState.isAccountConfigured,
                    onToggle = onToggleOnCall,
                    onConfigureClick = onNavigateToAccountConfig
                )
            }

            // Battery Optimization Guide Card
            if (uiState.showBatteryPrompt) {
                item {
                    BatteryPromptCard(
                        manufacturer = uiState.manufacturer,
                        onDismiss = onDismissBatteryPrompt,
                        onOpenSettings = {
                            openBatterySettings(context)
                        }
                    )
                }
            }

            // Quick Hub Navigation Cards
            item {
                QuickHubSection(
                    pendingCount = uiState.pendingAlertsCount,
                    mailCount = uiState.totalMailsCount,
                    onNavigateToMailList = onNavigateToMailList,
                    onNavigateToHistory = onNavigateToHistory
                )
            }

            // Recent Incoming Mails Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Messages",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "View all",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onNavigateToMailList() }
                            .padding(4.dp)
                    )
                }
            }

            if (recentMails.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No messages received yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (uiState.isAccountConfigured) "Sync will check for new emails every 60s when on-call." else "Configure your IMAP account to start.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                items(recentMails.take(5), key = { it.id }) { mail ->
                    RecentMailItem(mail = mail, onClick = { onNavigateToMailDetail(mail.id) })
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ActiveAlertBanner(
    activeAlert: com.example.service.ActiveAlertState,
    onOpenAlert: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAlert() }
            .testTag("active_alert_banner"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CRITICAL ALERT RINGING",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activeAlert.subject,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onOpenAlert,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFB91C1C)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "View", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OnCallToggleCard(
    isActive: Boolean,
    isConfigured: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigureClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF14532D) else MaterialTheme.colorScheme.surfaceVariant,
        label = "onCallBg"
    )
    val contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("on_call_toggle_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF4ADE80) else contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isActive) "ON-CALL ACTIVE" else "ON-CALL STANDBY",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                        Text(
                            text = if (isActive) "Emergency alerts enabled" else "Alerting paused",
                            fontSize = 13.sp,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }

                Switch(
                    checked = isActive,
                    onCheckedChange = { checked ->
                        if (isConfigured) {
                            onToggle(checked)
                        } else {
                            onConfigureClick()
                        }
                    },
                    modifier = Modifier.testTag("on_call_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF22C55E)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isConfigured) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Account not configured",
                            color = Color(0xFF92400E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Configure Now",
                            color = Color(0xFFB45309),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onConfigureClick() }
                        )
                    }
                }
            } else {
                Text(
                    text = if (isActive)
                        "The app continuously monitors the shared mailbox every 60s. Incoming mail triggers a persistent full-screen alarm."
                    else
                        "You are currently off-duty. Activate when taking over the on-call shift.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun BatteryPromptCard(
    manufacturer: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Background Reliability Setup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val isSamsung = manufacturer.contains("samsung", ignoreCase = true)
            Text(
                text = if (isSamsung) {
                    "Samsung devices aggressively sleep background apps. Please disable battery optimization and add Asteintus to 'Never sleeping apps' in Device Care settings."
                } else {
                    "To ensure alerts ring immediately while your phone is locked, please disable battery optimization for Asteintus."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Dismiss", fontSize = 12.sp)
                }
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Configure Settings", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun QuickHubSection(
    pendingCount: Int,
    mailCount: Int,
    onNavigateToMailList: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mail List Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToMailList() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "$mailCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Mailbox Inbox",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // History Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigateToHistory() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (pendingCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$pendingCount",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pendingCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pending Alerts",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentMailItem(mail: Mail, onClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val formattedDate = dateFormatter.format(Date(mail.receivedDate))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mail.senderName.ifBlank { mail.senderAddress },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = mail.subject,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = mail.bodyExcerpt,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status badge
            val (badgeBg, badgeFg) = when (mail.status) {
                MailStatus.NEW -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626))
                MailStatus.ACKNOWLEDGED -> Pair(Color(0xFFDCFCE7), Color(0xFF16A34A))
                MailStatus.SNOOZED -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
                MailStatus.READ -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
            }

            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = mail.status.name,
                    color = badgeFg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun openBatterySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }
}
