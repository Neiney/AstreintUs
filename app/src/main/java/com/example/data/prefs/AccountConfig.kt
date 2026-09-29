package com.example.data.prefs

enum class SecurityType {
    SSL_TLS,
    STARTTLS
}

data class AccountConfig(
    val emailAddress: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val mailbox: String = "INBOX/ONCALL",
    val username: String = "",
    val password: String = "",
    val securityType: SecurityType = SecurityType.SSL_TLS,
    val isMdmLocked: Boolean = false
) {
    val isConfigured: Boolean
        get() = emailAddress.isNotBlank() &&
                imapHost.isNotBlank() &&
                username.isNotBlank() &&
                password.isNotBlank()
}
