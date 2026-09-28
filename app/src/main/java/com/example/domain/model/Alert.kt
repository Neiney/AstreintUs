package com.example.domain.model

enum class AlertStatus {
    PENDING,
    ACKNOWLEDGED,
    SNOOZED
}

data class Alert(
    val id: Long = 0,
    val mailId: Long,
    val mailUid: Long,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val receivedTime: Long,
    val status: AlertStatus = AlertStatus.PENDING,
    val acknowledgedTime: Long? = null,
    val reactionTimeSeconds: Long? = null,
    val snoozedUntil: Long? = null
) {
    val reactionTimeFormatted: String
        get() {
            val seconds = reactionTimeSeconds ?: return "—"
            val mins = seconds / 60
            val secs = seconds % 60
            return "${mins}m ${secs}s"
        }
}
