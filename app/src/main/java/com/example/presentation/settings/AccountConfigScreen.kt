package com.example.presentation.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.AccountConfig
import com.example.data.prefs.SecurityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountConfigScreen(
    currentConfig: AccountConfig,
    isTesting: Boolean,
    testResult: String?,
    isTestSuccess: Boolean,
    onTestConnection: (AccountConfig) -> Unit,
    onSaveConfig: (AccountConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    var emailAddress by remember(currentConfig) { mutableStateOf(currentConfig.emailAddress) }
    var imapHost by remember(currentConfig) { mutableStateOf(currentConfig.imapHost) }
    var imapPort by remember(currentConfig) { mutableStateOf(currentConfig.imapPort.toString()) }
    var mailbox by remember(currentConfig) { mutableStateOf(currentConfig.mailbox) }
    var username by remember(currentConfig) { mutableStateOf(currentConfig.username) }
    var password by remember(currentConfig) { mutableStateOf(currentConfig.password) }
    var securityType by remember(currentConfig) { mutableStateOf(currentConfig.securityType) }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    fun buildConfig(): AccountConfig {
        val parsedImapPort = imapPort.toIntOrNull() ?: if (securityType == SecurityType.SSL_TLS) 993 else 143
        return AccountConfig(
            emailAddress = emailAddress.trim(),
            imapHost = imapHost.trim(),
            imapPort = parsedImapPort,
            mailbox = mailbox.trim().ifBlank { "INBOX/ONCALL" },
            username = username.trim(),
            password = password,
            securityType = securityType,
            isMdmLocked = currentConfig.isMdmLocked
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configuration IMAP d'Astreinte", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MDM Managed banner if locked
            if (currentConfig.isMdmLocked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Politique Entreprise MDM Active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Les paramètres serveur et dossier sont imposés et verrouillés par votre organisation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Security Notice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Stockage Chiffré Matériel",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Vos identifiants sont chiffrés par l'Android Keystore (AES-256 GCM) et la base locale est protégée par SQLCipher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Connection Test Result Banner
            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTestSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isTestSuccess)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = testResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isTestSuccess)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Email Address
            OutlinedTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = { Text("Adresse e-mail d'astreinte") },
                placeholder = { Text("astreinte@votre-entreprise.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_email_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            // Dedicated Mailbox Folder
            OutlinedTextField(
                value = mailbox,
                onValueChange = { if (!currentConfig.isMdmLocked) mailbox = it },
                enabled = !currentConfig.isMdmLocked,
                label = { Text("Dossier IMAP dédié (Strictement contrôlé)") },
                placeholder = { Text("INBOX/ONCALL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_mailbox_input"),
                singleLine = true,
                supportingText = {
                    Text("Ex: INBOX/ONCALL. Aucun mail humain ou newsletter ne doit y transiter.")
                }
            )

            // Security Type selector
            Column {
                Text(
                    text = "Chiffrement du protocole IMAP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = securityType == SecurityType.SSL_TLS,
                        onClick = {
                            securityType = SecurityType.SSL_TLS
                            if (imapPort.isBlank() || imapPort == "143") imapPort = "993"
                        },
                        label = { Text("SSL / TLS (Port 993)") }
                    )
                    FilterChip(
                        selected = securityType == SecurityType.STARTTLS,
                        onClick = {
                            securityType = SecurityType.STARTTLS
                            if (imapPort.isBlank() || imapPort == "993") imapPort = "143"
                        },
                        label = { Text("STARTTLS") }
                    )
                }
            }

            // IMAP Server & Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = imapHost,
                    onValueChange = { if (!currentConfig.isMdmLocked) imapHost = it },
                    enabled = !currentConfig.isMdmLocked,
                    label = { Text("Serveur hôte IMAP") },
                    placeholder = { Text("imap.exemple.com") },
                    modifier = Modifier
                        .weight(2.5f)
                        .testTag("account_imap_host_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = imapPort,
                    onValueChange = { if (!currentConfig.isMdmLocked) imapPort = it },
                    enabled = !currentConfig.isMdmLocked,
                    label = { Text("Port") },
                    placeholder = { Text(if (securityType == SecurityType.SSL_TLS) "993" else "143") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("account_imap_port_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nom d'utilisateur / Identifiant") },
                placeholder = { Text("identifiant ou adresse e-mail") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_username_input"),
                singleLine = true
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe / Mot de passe d'application") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_password_input"),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Masquer mot de passe" else "Afficher mot de passe"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Button(
                onClick = { onSaveConfig(buildConfig()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_save_button")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer les paramètres")
            }

            OutlinedButton(
                onClick = { onTestConnection(buildConfig()) },
                enabled = !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_test_button")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test de connexion en cours...")
                } else {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tester la connexion au dossier")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
