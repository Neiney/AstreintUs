package com.example.domain.alert

import com.example.data.remote.imap.RemoteMailMessage

data class AlertPolicy(
    val mailbox: String,
    val allowedSenders: Set<String>,
    val requiredSubjectPrefix: String
) {
    val isConfigured: Boolean
        get() = mailbox.isNotBlank() && allowedSenders.isNotEmpty() && requiredSubjectPrefix.isNotBlank()

    fun accepts(mail: RemoteMailMessage): Boolean = accepts(mail.senderAddress, mail.subject)

    fun accepts(senderAddress: String, subject: String): Boolean {
        val sender = senderAddress.trim().lowercase()
        return sender in allowedSenders.map { it.lowercase() } &&
            subject.startsWith(requiredSubjectPrefix, ignoreCase = true)
    }
}
