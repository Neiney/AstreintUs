package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mail_messages",
    indices = [Index(value = ["uid"], unique = true)]
)
data class MailMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: Long,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val receivedDate: Long,
    val bodyExcerpt: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val status: String // "NEW", "READ", "ACKNOWLEDGED", "SNOOZED"
)
