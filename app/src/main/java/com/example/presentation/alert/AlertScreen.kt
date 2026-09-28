package com.example.presentation.alert

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Alert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertScreen(
    uiState: AlertUiState,
    onAcknowledge: (Long) -> Unit,
    onSnooze: (Long, Int) -> Unit,
    onOpenMail: (Long) -> Unit,
    onMuteRingtone: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // High contrast dark midnight canvas
    ) {
        when (uiState) {
            is AlertUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE53935))
                }
            }
            is AlertUiState.Dismissed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Alert Resolved",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Alert Resolved",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            is AlertUiState.Active -> {
                ActiveAlertContent(
                    alert = uiState.alert,
                    isRinging = uiState.isRinging,
                    isMuted = uiState.isMuted,
                    queueCount = uiState.queueCount,
                    onAcknowledge = { onAcknowledge(uiState.alert.id) },
                    onSnooze = { minutes -> onSnooze(uiState.alert.id, minutes) },
                    onOpenMail = { onOpenMail(uiState.alert.mailId) },
                    onMuteRingtone = onMuteRingtone
                )
            }
        }
    }
}

@Composable
private fun ActiveAlertContent(
    alert: Alert,
    isRinging: Boolean,
    isMuted: Boolean,
    queueCount: Int,
    onAcknowledge: () -> Unit,
    onSnooze: (Int) -> Unit,
    onOpenMail: () -> Unit,
    onMuteRingtone: () -> Unit
) {
    val scrollState = rememberScrollState()

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val beaconAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormatter.format(Date(alert.receivedTime))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Beacon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFE53935).copy(alpha = if (isRinging) beaconAlpha * 0.25f else 0.15f))
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isRinging) Icons.Default.NotificationsActive else Icons.Default.Warning,
                    contentDescription = "Critical Alert Indicator",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "NEW ALERT",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Color(0xFFEF4444),
                modifier = Modifier.testTag("alert_header_title")
            )

            if (queueCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "+$queueCount more alert${if (queueCount > 1) "s" else ""} queued",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            if (isMuted) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Audio Muted (Alert Active)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card displaying From, Subject, Received at
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFDC2626), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sender
                Column {
                    Text(
                        text = "From:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${alert.senderName} <${alert.senderAddress}>",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.testTag("alert_sender_text")
                    )
                }

                // Subject
                Column {
                    Text(
                        text = "Subject:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = alert.subject,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFCA5A5), // High contrast reddish tint
                        modifier = Modifier.testTag("alert_subject_text")
                    )
                }

                // Received at
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Received at:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.testTag("alert_time_text")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mute ringtone button (if ringing)
            AnimatedVisibility(visible = isRinging) {
                OutlinedButton(
                    onClick = onMuteRingtone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("mute_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeMute,
                        contentDescription = "Mute Ringtone",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Silence Ringtone (Volume Key)", fontSize = 15.sp)
                }
            }

            // ACKNOWLEDGE Button (Prominent Green)
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("acknowledge_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A), // PagerDuty green
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "— ACKNOWLEDGE —",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            // Snooze Buttons: 5 min | 15 min | 30 min
            Text(
                text = "Snooze Alert",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSnooze(5) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("snooze_5_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "5 min", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { onSnooze(15) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("snooze_15_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "15 min", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { onSnooze(30) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("snooze_30_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "30 min", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Open Mail Button
            OutlinedButton(
                onClick = onOpenMail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_mail_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF38BDF8)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open mail",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
