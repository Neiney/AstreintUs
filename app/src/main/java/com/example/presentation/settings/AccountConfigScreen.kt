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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var smtpHost by remember(currentConfig) { mutableStateOf(currentConfig.smtpHost) }
    var smtpPort by remember(currentConfig) { mutableStateOf(currentConfig.smtpPort.toString()) }
    var username by remember(currentConfig) { mutableStateOf(currentConfig.username) }
    var password by remember(currentConfig) { mutableStateOf(currentConfig.password) }
    var securityType by remember(currentConfig) { mutableStateOf(currentConfig.securityType) }
    var passwordVisible by remember { mutableStateOf(false) }

    var saveNotice by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun buildConfig(): AccountConfig {
        val parsedImapPort = imapPort.toIntOrNull() ?: if (securityType == SecurityType.SSL_TLS) 993 else 143
        val parsedSmtpPort = smtpPort.toIntOrNull() ?: if (securityType == SecurityType.SSL_TLS) 465 else 587
        return AccountConfig(
            emailAddress = emailAddress.trim(),
            imapHost = imapHost.trim(),
            imapPort = parsedImapPort,
            smtpHost = smtpHost.trim(),
            smtpPort = parsedSmtpPort,
            username = username.trim(),
            password = password,
            securityType = securityType
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Enter the shared mailbox IMAP credentials. These are securely encrypted on your device using Android Keystore.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Test Connection Result Banner (if available)
            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTestSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isTestSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = testResult,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isTestSuccess) Color(0xFF15803D) else Color(0xFFB91C1C)
                        )
                    }
                }
            }

            if (saveNotice) {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Account settings saved successfully!",
                        color = Color(0xFF15803D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Security Mode Chips
            Text(
                text = "Security Type",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SecurityType.SSL_TLS, SecurityType.STARTTLS).forEach { type ->
                    val label = when (type) {
                        SecurityType.SSL_TLS -> "SSL/TLS"
                        SecurityType.STARTTLS -> "STARTTLS"
                    }
                    FilterChip(
                        selected = securityType == type,
                        onClick = {
                            securityType = type
                            if (type == SecurityType.SSL_TLS) {
                                if (imapPort.isBlank() || imapPort == "143") imapPort = "993"
                                if (smtpPort.isBlank() || smtpPort == "587") smtpPort = "465"
                            } else if (type == SecurityType.STARTTLS) {
                                if (imapPort.isBlank() || imapPort == "993") imapPort = "143"
                                if (smtpPort.isBlank() || smtpPort == "465") smtpPort = "587"
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }

            // Email Address
            OutlinedTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = { Text("Email Address") },
                placeholder = { Text("oncall@company.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_email_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            // IMAP Server & Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = imapHost,
                    onValueChange = { imapHost = it },
                    label = { Text("IMAP Server Host") },
                    placeholder = { Text("imap.example.com") },
                    modifier = Modifier
                        .weight(2.5f)
                        .testTag("account_imap_host_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = imapPort,
                    onValueChange = { imapPort = it },
                    label = { Text("Port") },
                    placeholder = { Text(if (securityType == SecurityType.SSL_TLS) "993" else "143") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("account_imap_port_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // SMTP Server & Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = smtpHost,
                    onValueChange = { smtpHost = it },
                    label = { Text("SMTP Server Host") },
                    placeholder = { Text("smtp.example.com") },
                    modifier = Modifier
                        .weight(2.5f)
                        .testTag("account_smtp_host_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = smtpPort,
                    onValueChange = { smtpPort = it },
                    label = { Text("Port") },
                    placeholder = { Text(if (securityType == SecurityType.SSL_TLS) "465" else "587") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("account_smtp_port_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("username or email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_username_input"),
                singleLine = true
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password / App Password") },
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
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Test Connection Button
                OutlinedButton(
                    onClick = {
                        saveNotice = false
                        onTestConnection(buildConfig())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("test_connection_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTesting && emailAddress.isNotBlank() && imapHost.isNotBlank()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...", fontSize = 14.sp)
                    } else {
                        Icon(imageVector = Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", fontSize = 14.sp)
                    }
                }

                // Save Button
                Button(
                    onClick = {
                        onSaveConfig(buildConfig())
                        saveNotice = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("save_account_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
