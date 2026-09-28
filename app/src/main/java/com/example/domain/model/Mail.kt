package com.example.domain.model

enum class MailStatus {
    NEW,
    READ,
    ACKNOWLEDGED,
    SNOOZED
}

data class Mail(
    val id: Long = 0,
    val uid: Long,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val receivedDate: Long,
    val bodyExcerpt: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val status: MailStatus = MailStatus.NEW
)
