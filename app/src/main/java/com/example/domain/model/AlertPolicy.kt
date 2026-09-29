package com.example.domain.model

data class AlertPolicy(
    val mailbox: String = "INBOX/ONCALL",
    val allowedSenders: Set<String> = emptySet(),
    val allowedDomains: Set<String> = emptySet(),
    val requiredHeaders: Map<String, String> = emptyMap(),
    val subjectPattern: String = "",
    val version: Long = 1L,
    val isMdmManaged: Boolean = false
) {
    fun isEligible(
        folderName: String,
        senderAddress: String,
        subject: String,
        headers: Map<String, String>,
        uid: Long,
        uidValidity: Long
    ): EligibilityResult {
        // 1. Folder check
        val normalizedExpected = mailbox.trim().lowercase()
        val normalizedActual = folderName.trim().lowercase()
        if (normalizedActual != normalizedExpected && !normalizedActual.endsWith("/$normalizedExpected")) {
            return EligibilityResult(
                isEligible = false,
                reason = "Dossier non éligible: attendu '$mailbox', reçu '$folderName'"
            )
        }

        // 2. Allowed Senders (if defined)
        val normalizedSender = senderAddress.trim().lowercase()
        if (allowedSenders.isNotEmpty()) {
            val senderMatch = allowedSenders.any { it.trim().lowercase() == normalizedSender }
            if (!senderMatch) {
                return EligibilityResult(
                    isEligible = false,
                    reason = "Expéditeur '$senderAddress' non autorisé par la politique"
                )
            }
        }

        // 3. Allowed Domains (if defined)
        val senderDomain = normalizedSender.substringAfterLast('@', "")
        if (allowedDomains.isNotEmpty()) {
            val domainMatch = allowedDomains.any { it.trim().lowercase() == senderDomain }
            if (!domainMatch) {
                return EligibilityResult(
                    isEligible = false,
                    reason = "Domaine d'expéditeur '$senderDomain' non autorisé"
                )
            }
        }

        // 4. Required Headers (if defined)
        for ((key, expectedValue) in requiredHeaders) {
            val actualValue = headers[key] ?: headers[key.lowercase()]
            if (actualValue == null || !actualValue.equals(expectedValue, ignoreCase = true)) {
                return EligibilityResult(
                    isEligible = false,
                    reason = "En-tête requis '$key=$expectedValue' manquant ou non conforme"
                )
            }
        }

        // 5. Subject pattern Regex (if defined)
        if (subjectPattern.isNotBlank()) {
            try {
                val regex = Regex(subjectPattern, RegexOption.IGNORE_CASE)
                if (!regex.containsMatchIn(subject)) {
                    return EligibilityResult(
                        isEligible = false,
                        reason = "Sujet ne correspond pas au motif d'alerte '$subjectPattern'"
                    )
                }
            } catch (e: Exception) {
                // If regex syntax is invalid, log and don't block unless strict
            }
        }

        // 6. Extract Incident ID (from headers or subject)
        val incidentId = extractIncidentId(headers, subject)

        // 7. Robust Deduplication key (Incident ID if present, otherwise UIDVALIDITY + UID)
        val dedupKey = if (!incidentId.isNullOrBlank()) {
            "inc:$incidentId"
        } else {
            "uid:$uidValidity:$uid"
        }

        return EligibilityResult(
            isEligible = true,
            reason = "Conforme à la politique v$version",
            incidentId = incidentId,
            dedupKey = dedupKey
        )
    }

    private fun extractIncidentId(headers: Map<String, String>, subject: String): String? {
        val headerCandidates = listOf(
            "X-Incident-ID", "x-incident-id",
            "X-Alert-ID", "x-alert-id",
            "X-Opsgenie-AlertId", "X-PagerDuty-IncidentKey"
        )
        for (candidate in headerCandidates) {
            val value = headers[candidate]
            if (!value.isNullOrBlank()) {
                return value.trim()
            }
        }

        // Regex in subject: e.g. [INC-12345], #INC-987, ALERT-456, CRIT-789
        val subjectRegex = Regex("""(?:\[|\b)(INC-\d+|CRIT-\d+|ALERT-[A-Za-z0-9_-]+)(?:\]|\b)""", RegexOption.IGNORE_CASE)
        val match = subjectRegex.find(subject)
        return match?.groupValues?.get(1)
    }
}

data class EligibilityResult(
    val isEligible: Boolean,
    val reason: String,
    val incidentId: String? = null,
    val dedupKey: String? = null
)
