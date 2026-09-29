package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AlertEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert_events ORDER BY receivedTime DESC")
    fun getAllAlerts(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE status IN ('PENDING', 'RINGING') ORDER BY receivedTime ASC")
    fun getPendingAlerts(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE status IN ('PENDING', 'RINGING') ORDER BY receivedTime ASC")
    suspend fun getPendingAlertsSync(): List<AlertEventEntity>

    @Query("SELECT * FROM alert_events WHERE status IN ('PENDING', 'RINGING', 'MUTED') ORDER BY receivedTime ASC")
    suspend fun getActiveAlertsSync(): List<AlertEventEntity>

    @Query("SELECT * FROM alert_events WHERE id = :id LIMIT 1")
    fun getAlertById(id: Long): Flow<AlertEventEntity?>

    @Query("SELECT * FROM alert_events WHERE id = :id LIMIT 1")
    suspend fun getAlertByIdSync(id: Long): AlertEventEntity?

    @Query("SELECT * FROM alert_events WHERE dedupKey = :dedupKey LIMIT 1")
    suspend fun getAlertByDedupKey(dedupKey: String): AlertEventEntity?

    @Query("SELECT * FROM alert_events WHERE mailUid = :mailUid LIMIT 1")
    suspend fun getAlertByMailUid(mailUid: Long): AlertEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: AlertEventEntity): Long

    @Update
    suspend fun updateAlert(alert: AlertEventEntity)

    @Query("UPDATE alert_events SET status = :status WHERE id = :id")
    suspend fun updateAlertStatus(id: Long, status: String)

    @Query("UPDATE alert_events SET status = 'ACKNOWLEDGED', acknowledgedTime = :ackTime, reactionTimeSeconds = :reactionSeconds WHERE id = :id")
    suspend fun acknowledgeAlert(id: Long, ackTime: Long, reactionSeconds: Long)

    @Query("UPDATE alert_events SET status = 'SNOOZED', snoozedUntil = :snoozedUntil WHERE id = :id")
    suspend fun snoozeAlert(id: Long, snoozedUntil: Long)

    @Query("UPDATE alert_events SET status = 'PENDING', snoozedUntil = NULL WHERE status = 'SNOOZED' AND snoozedUntil <= :currentTime")
    suspend fun reactivateExpiredSnoozes(currentTime: Long): Int

    @Query("DELETE FROM alert_events WHERE receivedTime < :thresholdTimestamp")
    suspend fun deleteAlertsOlderThan(thresholdTimestamp: Long): Int

    @Query("DELETE FROM alert_events")
    suspend fun deleteAllAlerts()
}
