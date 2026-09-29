package com.example.domain.model

enum class HealthState {
    /**
     * IMAP IDLE active, permissions granted, latency SLA (< 3 min) strictly respected.
     */
    READY,

    /**
     * Transient reconnection in progress, cellular handover, or sync lag > 90s.
     */
    DEGRADED,

    /**
     * Missing critical permissions, bad authentication, or non-compliant MDM posture.
     */
    BLOCKED,

    /**
     * Latency SLA compromised (> 180s without successful sync while on-call is active).
     * Secondary escalation channel must be triggered.
     */
    ESCALATING,

    /**
     * On-call standby is inactive.
     */
    OFF
}

data class HealthTelemetry(
    val state: HealthState = HealthState.OFF,
    val mailbox: String = "INBOX/ONCALL",
    val isIdleActive: Boolean = false,
    val lastHealthySyncTimestamp: Long = 0L,
    val reconnectCount: Int = 0,
    val lastErrorMessage: String? = null,
    val lastProcessedUid: Long = 0L,
    val folderUidValidity: Long = 0L,
    val policyVersion: Long = 1L,
    val isMdmManaged: Boolean = false
) {
    val syncAgeSeconds: Long
        get() = if (lastHealthySyncTimestamp > 0) {
            maxOf(0L, (System.currentTimeMillis() - lastHealthySyncTimestamp) / 1000L)
        } else -1L

    val isSlaCompromised: Boolean
        get() = state == HealthState.ESCALATING || (state != HealthState.OFF && syncAgeSeconds > 180L)
}
