package com.example.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

data class PostureReport(
    val isDeviceSecure: Boolean,
    val isNotificationsEnabled: Boolean,
    val isBatteryExempt: Boolean,
    val isCompliant: Boolean,
    val issues: List<String>
)

object PostureChecker {

    fun checkPosture(context: Context): PostureReport {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isSecure = keyguard?.isDeviceSecure ?: false

        val notifManager = NotificationManagerCompat.from(context)
        val notifsEnabled = notifManager.areNotificationsEnabled()

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryExempt = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val issues = mutableListOf<String>()
        if (!isSecure) {
            issues.add("L'appareil n'est pas protégé par un verrouillage sécurisé (PIN/Schéma/Biométrie).")
        }
        if (!notifsEnabled) {
            issues.add("Les notifications d'Asteintus sont désactivées dans les paramètres système.")
        }
        if (!batteryExempt) {
            issues.add("L'application n'est pas exemptée de l'optimisation batterie (risque de suspension Doze).")
        }

        return PostureReport(
            isDeviceSecure = isSecure,
            isNotificationsEnabled = notifsEnabled,
            isBatteryExempt = batteryExempt,
            isCompliant = notifsEnabled, // notifications are strictly mandatory for SLA
            issues = issues
        )
    }
}
