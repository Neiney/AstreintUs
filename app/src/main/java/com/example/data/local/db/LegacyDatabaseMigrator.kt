package com.example.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.data.local.entity.AlertEventEntity
import com.example.data.local.entity.MailMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal object LegacyDatabaseMigrator {
    private const val TAG = "LegacyDbMigrator"
    private const val LEGACY_DB_NAME = "asteintus.db"

    fun migrateIfPresent(context: Context, encryptedDatabase: AppDatabase) {
        val legacyFile = context.getDatabasePath(LEGACY_DB_NAME)
        if (!legacyFile.exists()) return

        try {
            val legacy = SQLiteDatabase.openDatabase(
                legacyFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val mails = legacy.query("mail_messages", null, null, null, null, null, null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toMail())
                }
            }
            val alerts = legacy.query("alert_events", null, null, null, null, null, null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toAlert())
                }
            }
            legacy.close()

            runBlocking(Dispatchers.IO) {
                encryptedDatabase.mailDao().insertMails(mails)
                encryptedDatabase.alertDao().insertAlerts(alerts)
            }

            if (!legacyFile.delete()) {
                Log.w(TAG, "Legacy database copied but could not be deleted.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Legacy database migration failed; keeping source database.", e)
        }
    }

    private fun android.database.Cursor.toMail() = MailMessageEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        uid = getLong(getColumnIndexOrThrow("uid")),
        senderName = getString(getColumnIndexOrThrow("senderName")),
        senderAddress = getString(getColumnIndexOrThrow("senderAddress")),
        subject = getString(getColumnIndexOrThrow("subject")),
        receivedDate = getLong(getColumnIndexOrThrow("receivedDate")),
        bodyExcerpt = getString(getColumnIndexOrThrow("bodyExcerpt")),
        bodyText = getString(getColumnIndexOrThrow("bodyText")),
        bodyHtml = getStringOrNull("bodyHtml"),
        status = getString(getColumnIndexOrThrow("status"))
    )

    private fun android.database.Cursor.toAlert() = AlertEventEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        mailId = getLong(getColumnIndexOrThrow("mailId")),
        mailUid = getLong(getColumnIndexOrThrow("mailUid")),
        senderName = getString(getColumnIndexOrThrow("senderName")),
        senderAddress = getString(getColumnIndexOrThrow("senderAddress")),
        subject = getString(getColumnIndexOrThrow("subject")),
        receivedTime = getLong(getColumnIndexOrThrow("receivedTime")),
        status = getString(getColumnIndexOrThrow("status")),
        acknowledgedTime = getLongOrNull("acknowledgedTime"),
        reactionTimeSeconds = getLongOrNull("reactionTimeSeconds"),
        snoozedUntil = getLongOrNull("snoozedUntil")
    )

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }
}
