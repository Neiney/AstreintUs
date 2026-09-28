package com.example.domain.repository

import com.example.domain.model.Mail
import com.example.domain.model.MailStatus
import kotlinx.coroutines.flow.Flow

interface MailRepository {
    fun getAllMails(): Flow<List<Mail>>
    fun getMailById(id: Long): Flow<Mail?>
    suspend fun getMailByIdSync(id: Long): Mail?
    suspend fun getMailByUid(uid: Long): Mail?
    suspend fun updateMailStatus(id: Long, status: MailStatus)
    suspend fun fetchAndSaveNewMails(): Result<List<Mail>>
    suspend fun testConnection(): Result<String>
    suspend fun deleteAllMails()
}
