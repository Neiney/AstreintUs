package com.example.domain.alert

import com.example.data.remote.imap.RemoteMailMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEligibilityPolicyTest {

    private val policy = AlertPolicy(
        mailbox = "INBOX/ONCALL",
        allowedSenders = setOf("alerts@monitoring.example"),
        requiredSubjectPrefix = "[P1]"
    )

    @Test
    fun `accepts a mail matching mailbox sender and subject`() {
        assertTrue(policy.accepts(mail("alerts@monitoring.example", "[P1] Database unavailable")))
    }

    @Test
    fun `rejects a mail from an untrusted sender`() {
        assertFalse(policy.accepts(mail("attacker@example", "[P1] Database unavailable")))
    }

    @Test
    fun `rejects a mail without the required priority prefix`() {
        assertFalse(policy.accepts(mail("alerts@monitoring.example", "[P3] Disk warning")))
    }

    private fun mail(sender: String, subject: String) = RemoteMailMessage(
        uid = 1,
        senderName = sender,
        senderAddress = sender,
        subject = subject,
        receivedDate = 0,
        bodyExcerpt = "",
        bodyText = ""
    )
}
