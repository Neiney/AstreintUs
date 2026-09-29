package com.example.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.entity.AlertEventEntity
import com.example.data.local.entity.MailMessageEntity
import com.example.data.prefs.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [MailMessageEntity::class, AlertEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mailDao(): MailDao
    abstract fun alertDao(): AlertDao

    companion object {
        const val isCipherActive: Boolean = true
        private const val TAG = "AppDatabase"
        private const val ENCRYPTED_DB_NAME = "asteintus_encrypted.db"
        private const val LEGACY_UNENCRYPTED_DB_NAME = "asteintus.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            // Load native SQLCipher libraries
            try {
                System.loadLibrary("sqlcipher")
            } catch (e: Throwable) {
                Log.w(TAG, "SQLCipher native libraries load notice: ${e.message}")
            }

            // Step 2 of Lot 1: Detect and purge legacy unencrypted database for security upgrade
            purgeLegacyUnencryptedDb(appContext)

            val passphrase = DatabaseKeyProvider.getOrCreatePassphrase(appContext)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                ENCRYPTED_DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }

        private fun purgeLegacyUnencryptedDb(context: Context) {
            try {
                val legacyDb = context.getDatabasePath(LEGACY_UNENCRYPTED_DB_NAME)
                if (legacyDb.exists()) {
                    Log.w(TAG, "Legacy unencrypted database detected. Purging unencrypted cache for security upgrade.")
                    context.deleteDatabase(LEGACY_UNENCRYPTED_DB_NAME)
                    File("${legacyDb.path}-wal").delete()
                    File("${legacyDb.path}-shm").delete()
                    File("${legacyDb.path}-journal").delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while checking or removing legacy unencrypted db", e)
            }
        }
    }
}
