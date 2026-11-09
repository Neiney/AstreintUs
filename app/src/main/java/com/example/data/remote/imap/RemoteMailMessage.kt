package com.example.data.remote.imap

data class RemoteMailMessage(
    val uid: Long,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val receivedDate: Long,
    val bodyExcerpt: String,
    val bodyText: String,
    val bodyHtml: String? = null
)
