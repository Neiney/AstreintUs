package com.example.presentation.maildetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.Mail
import com.example.domain.model.MailStatus
import com.example.domain.model.SnoozeOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    uiState: MailDetailUiState,
    onNavigateBack: () -> Unit,
    onToggleHtml: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onAcknowledgeAlert: (Long) -> Unit,
    onSnoozeAlert: (Long, SnoozeOption) -> Unit
) {
    val context = LocalContext.current
    var urlToConfirm by remember { mutableStateOf<String?>(null) }

    // Dialog confirmation for external link navigation
    if (urlToConfirm != null) {
        val targetUrl = urlToConfirm!!
        AlertDialog(
            onDismissRequest = { urlToConfirm = null },
            icon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
            title = { Text("Ouvrir le lien externe ?") },
            text = {
                Text("Pour des raisons de sécurité, les liens externes ne s'ouvrent pas dans l'application :\n\n$targetUrl")
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                        urlToConfirm = null
                    }
                ) {
                    Text("Ouvrir dans le navigateur")
                }
            },
            dismissButton = {
                TextButton(onClick = { urlToConfirm = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Message d'Astreinte", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is MailDetailUiState.Success) {
                        val isRead = uiState.mail.status == MailStatus.READ
                        IconButton(onClick = onToggleReadStatus) {
                            Icon(
                                imageVector = if (isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                contentDescription = if (isRead) "Marquer non lu" else "Marquer lu"
                            )
                        }
                        if (uiState.mail.bodyHtml != null) {
                            IconButton(onClick = onToggleHtml) {
                                Icon(
                                    imageVector = if (uiState.isHtmlMode) Icons.Default.TextFields else Icons.Default.Code,
                                    contentDescription = if (uiState.isHtmlMode) "Mode texte brut" else "Mode HTML sécurisé"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is MailDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is MailDetailUiState.NotFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Message introuvable ou supprimé par la purge de rétention.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is MailDetailUiState.Success -> {
                MailDetailContent(
                    mail = uiState.mail,
                    isHtmlMode = uiState.isHtmlMode,
                    associatedAlertId = uiState.associatedAlert?.id,
                    onAcknowledgeAlert = onAcknowledgeAlert,
                    onSnoozeAlert = onSnoozeAlert,
                    onLinkClick = { url -> urlToConfirm = url },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun MailDetailContent(
    mail: Mail,
    isHtmlMode: Boolean,
    associatedAlertId: Long?,
    onAcknowledgeAlert: (Long) -> Unit,
    onSnoozeAlert: (Long, SnoozeOption) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val formattedDate = remember(mail.receivedDate) {
        val sdf = SimpleDateFormat("dd MMMM yyyy à HH:mm:ss", Locale.getDefault())
        sdf.format(Date(mail.receivedDate))
    }

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Active Alert Banner if pending
        if (associatedAlertId != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚨 ALERTE CRITIQUE EN ATTENTE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAcknowledgeAlert(associatedAlertId) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Acquitter")
                        }

                        FilledTonalButton(
                            onClick = { onSnoozeAlert(associatedAlertId, SnoozeOption.FIVE_MINUTES) }
                        ) {
                            Icon(imageVector = Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("5m")
                        }
                    }
                }
            }
        }

        // Header Card: Subject, Sender, Date, Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = mail.subject,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("detail_subject")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mail.senderName.ifBlank { "Expéditeur Inconnu" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "<${mail.senderAddress}>",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = mail.status.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hardened Message Body
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHtmlMode) "Rendu HTML Sécurisé" else "Texte Brut (Recommandé)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isHtmlMode && mail.bodyHtml != null) {
                    // Security note: remote images blocked
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Images distantes (pixels traceurs) et scripts bloqués par mesure de sécurité.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    // Lot 1, step 4: Hardening
                                    javaScriptEnabled = false
                                    blockNetworkImage = true // Blocks remote tracking pixels / images
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    domStorageEnabled = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString()
                                        if (url != null) {
                                            onLinkClick(url)
                                        }
                                        return true // Prevent internal WebView navigation
                                    }
                                }
                                loadDataWithBaseURL(null, mail.bodyHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                } else {
                    Text(
                        text = mail.bodyText.ifBlank { mail.bodyExcerpt.ifBlank { "(Aucun contenu textuel)" } },
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("detail_body_text")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
