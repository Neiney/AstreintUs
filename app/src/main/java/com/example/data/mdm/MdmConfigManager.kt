package com.example.data.mdm

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Log
import com.example.domain.model.AlertPolicy

object MdmConfigManager {

    private const val TAG = "MdmConfigManager"

    const val KEY_MDM_IS_MANAGED = "mdm_is_managed"
    const val KEY_MDM_IMAP_HOST = "mdm_imap_host"
    const val KEY_MDM_IMAP_PORT = "mdm_imap_port"
    const val KEY_MDM_MAILBOX = "mdm_mailbox"
    const val KEY_MDM_ALLOWED_SENDERS = "mdm_allowed_senders"
    const val KEY_MDM_ALLOWED_DOMAINS = "mdm_allowed_domains"
    const val KEY_MDM_SUBJECT_REGEX = "mdm_subject_regex"
    const val KEY_MDM_POLICY_VERSION = "mdm_policy_version"

    fun getMdmPolicy(context: Context): AlertPolicy {
        val bundle = getRestrictions(context)
        if (bundle == null || bundle.isEmpty) {
            return AlertPolicy(
                mailbox = "INBOX/ONCALL",
                allowedSenders = emptySet(),
                allowedDomains = emptySet(),
                requiredHeaders = emptyMap(),
                subjectPattern = "",
                version = 1L,
                isMdmManaged = false
            )
        }

        val isManaged = bundle.getBoolean(KEY_MDM_IS_MANAGED, false) ||
                bundle.containsKey(KEY_MDM_IMAP_HOST) ||
                bundle.containsKey(KEY_MDM_MAILBOX)

        val mailbox = bundle.getString(KEY_MDM_MAILBOX, "INBOX/ONCALL").ifBlank { "INBOX/ONCALL" }
        val senders = bundle.getStringArray(KEY_MDM_ALLOWED_SENDERS)?.toSet()
            ?: bundle.getString(KEY_MDM_ALLOWED_SENDERS)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()
        val domains = bundle.getStringArray(KEY_MDM_ALLOWED_DOMAINS)?.toSet()
            ?: bundle.getString(KEY_MDM_ALLOWED_DOMAINS)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()
        val subjectRegex = bundle.getString(KEY_MDM_SUBJECT_REGEX, "") ?: ""
        val version = bundle.getLong(KEY_MDM_POLICY_VERSION, 1L)

        return AlertPolicy(
            mailbox = mailbox,
            allowedSenders = senders,
            allowedDomains = domains,
            requiredHeaders = emptyMap(),
            subjectPattern = subjectRegex,
            version = version,
            isMdmManaged = isManaged
        )
    }

    fun getMdmHost(context: Context): String? {
        val bundle = getRestrictions(context)
        return bundle?.getString(KEY_MDM_IMAP_HOST)?.takeIf { it.isNotBlank() }
    }

    fun getMdmPort(context: Context): Int? {
        val bundle = getRestrictions(context) ?: return null
        val port = bundle.getInt(KEY_MDM_IMAP_PORT, 0)
        return if (port in 1..65535) port else null
    }

    fun getMdmMailbox(context: Context): String? {
        val bundle = getRestrictions(context)
        return bundle?.getString(KEY_MDM_MAILBOX)?.takeIf { it.isNotBlank() }
    }

    fun isDeviceManaged(context: Context): Boolean {
        val bundle = getRestrictions(context)
        return bundle != null && !bundle.isEmpty && bundle.getBoolean(KEY_MDM_IS_MANAGED, false)
    }

    private fun getRestrictions(context: Context): Bundle? {
        return try {
            val restrictionsMgr = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            restrictionsMgr?.applicationRestrictions
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read MDM application restrictions", e)
            null
        }
    }
}
