package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MailMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {
    @Query("SELECT * FROM mail_messages ORDER BY receivedDate DESC")
    fun getAllMails(): Flow<List<MailMessageEntity>>

    @Query("SELECT * FROM mail_messages WHERE id = :id LIMIT 1")
    fun getMailById(id: Long): Flow<MailMessageEntity?>

    @Query("SELECT * FROM mail_messages WHERE id = :id LIMIT 1")
    suspend fun getMailByIdSync(id: Long): MailMessageEntity?

    @Query("SELECT * FROM mail_messages WHERE uid = :uid LIMIT 1")
    suspend fun getMailByUid(uid: Long): MailMessageEntity?

    @Query("SELECT * FROM mail_messages WHERE uid = :uid AND uidValidity = :uidValidity LIMIT 1")
    suspend fun getMailByUidAndValidity(uid: Long, uidValidity: Long): MailMessageEntity?

    @Query("SELECT MAX(uid) FROM mail_messages WHERE mailbox = :mailbox")
    suspend fun getMaxUidForMailbox(mailbox: String): Long?

    @Query("SELECT MAX(uid) FROM mail_messages")
    suspend fun getMaxUid(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMail(mail: MailMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMails(mails: List<MailMessageEntity>)

    @Query("UPDATE mail_messages SET status = :status WHERE id = :id")
    suspend fun updateMailStatus(id: Long, status: String)

    @Update
    suspend fun updateMail(mail: MailMessageEntity)

    @Query("DELETE FROM mail_messages WHERE receivedDate < :thresholdTimestamp")
    suspend fun deleteMailsOlderThan(thresholdTimestamp: Long): Int

    @Query("DELETE FROM mail_messages")
    suspend fun deleteAllMails()
}
