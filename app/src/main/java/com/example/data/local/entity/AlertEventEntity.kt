package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "alert_events", indices = [Index(value = ["mailbox", "mailUid"], unique = true)])
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "'INBOX'")
    val mailbox: String = "INBOX",
    val mailId: Long,
    val mailUid: Long,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val receivedTime: Long,
    val status: String, // "PENDING", "ACKNOWLEDGED", "SNOOZED"
    val acknowledgedTime: Long? = null,
    val reactionTimeSeconds: Long? = null,
    val snoozedUntil: Long? = null
)
