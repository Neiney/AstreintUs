package com.example.domain.repository

import com.example.domain.model.Alert
import com.example.domain.model.SnoozeOption
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    fun getAllAlerts(): Flow<List<Alert>>
    fun getPendingAlerts(): Flow<List<Alert>>
    suspend fun getPendingAlertsSync(): List<Alert>
    suspend fun getActiveAlertsSync(): List<Alert>
    fun getAlertById(id: Long): Flow<Alert?>
    suspend fun getAlertByIdSync(id: Long): Alert?
    suspend fun triggerAlertForMail(mailId: Long, dedupKey: String? = null, reason: String? = null): Result<Alert>
    suspend fun muteAlert(alertId: Long): Result<Unit>
    suspend fun acknowledgeAlert(alertId: Long): Result<Unit>
    suspend fun snoozeAlert(alertId: Long, snoozeOption: SnoozeOption): Result<Unit>
    suspend fun checkAndReactivateSnoozes(): Int
}
