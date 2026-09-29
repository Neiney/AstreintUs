package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alert_events",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["mailUid", "uidValidity"]),
        Index(value = ["status"])
    ]
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mailId: Long,
    val mailUid: Long,
    val uidValidity: Long = 0L,
    val mailbox: String = "INBOX/ONCALL",
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val receivedTime: Long,
    val status: String, // "NEW", "QUALIFIED", "PENDING", "RINGING", "MUTED", "SNOOZED", "ACKNOWLEDGED", "ESCALATED"
    val acknowledgedTime: Long? = null,
    val reactionTimeSeconds: Long? = null,
    val snoozedUntil: Long? = null,
    val incidentId: String? = null,
    val dedupKey: String? = null,
    val qualificationReason: String? = null
)
