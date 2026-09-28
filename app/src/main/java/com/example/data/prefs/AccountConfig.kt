package com.example.data.prefs

enum class SecurityType {
    SSL_TLS,
    STARTTLS
}

data class AccountConfig(
    val emailAddress: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val username: String = "",
    val password: String = "",
    val securityType: SecurityType = SecurityType.SSL_TLS
) {
    val isConfigured: Boolean
        get() = emailAddress.isNotBlank() &&
                imapHost.isNotBlank() &&
                username.isNotBlank() &&
                password.isNotBlank()
}
