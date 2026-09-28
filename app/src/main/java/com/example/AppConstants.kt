package com.example

object AppConstants {
    /**
     * GDPR Art. 5(1)(e) Storage Limitation policy.
     * Mails and alert logs older than 30 days are automatically purged from local storage.
     */
    const val DATA_RETENTION_DAYS = 30L

    /**
     * Pinned mail server certificate expiration date for security monitoring.
     */
    const val PINNED_CERTIFICATE_EXPIRATION_DATE = "2027-01-01"
}
