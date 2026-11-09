package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.entity.AlertEventEntity
import net.sqlcipher.database.SupportFactory
import com.example.data.local.entity.MailMessageEntity

@Database(
    entities = [MailMessageEntity::class, AlertEventEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mailDao(): MailDao
    abstract fun alertDao(): AlertDao

    companion object {
        const val isCipherActive: Boolean = true
        private const val DATABASE_NAME = "asteintus_secure.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(SupportFactory(DatabaseKeyProvider.getOrCreatePassphrase(context)))
                    .build()
                    .also {
                        LegacyDatabaseMigrator.migrateIfPresent(context.applicationContext, it)
                        INSTANCE = it
                    }
            }
        }
    }
}
