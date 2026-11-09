package com.example.data.prefs

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.example.domain.alert.AlertPolicy

class ManagedAlertPolicy(private val context: Context) {

    fun get(): AlertPolicy {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)
            ?.applicationRestrictions ?: Bundle.EMPTY
        val senders = restrictions.getString(KEY_ALLOWED_SENDERS, "")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

        return AlertPolicy(
            mailbox = restrictions.getString(KEY_MAILBOX, DEFAULT_MAILBOX).trim().ifBlank { DEFAULT_MAILBOX },
            allowedSenders = senders,
            requiredSubjectPrefix = restrictions.getString(KEY_SUBJECT_PREFIX, DEFAULT_SUBJECT_PREFIX)
        )
    }

    companion object {
        const val KEY_MAILBOX = "oncall_imap_mailbox"
        const val KEY_ALLOWED_SENDERS = "oncall_allowed_senders"
        const val KEY_SUBJECT_PREFIX = "oncall_subject_prefix"
        const val DEFAULT_MAILBOX = "INBOX/ONCALL"
        const val DEFAULT_SUBJECT_PREFIX = "[P1]"
    }
}
