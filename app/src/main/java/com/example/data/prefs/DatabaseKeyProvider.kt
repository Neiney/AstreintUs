package com.example.data.prefs

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object DatabaseKeyProvider {

    private const val KEY_PREFS_NAME = "asteintus_db_key_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_bytes"
    private const val KEY_SIZE_BYTES = 32 // 256 bits

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val securePrefs = try {
            createSecurePrefs(context)
        } catch (e: Exception) {
            android.util.Log.e("DatabaseKeyProvider", "Corrupted encrypted prefs, recreating", e)
            try {
                context.deleteSharedPreferences(KEY_PREFS_NAME)
            } catch (_: Exception) {}
            createSecurePrefs(context)
        }

        val existingBase64 = securePrefs.getString(KEY_DB_PASSPHRASE, null)
        return if (!existingBase64.isNullOrEmpty()) {
            Base64.decode(existingBase64, Base64.NO_WRAP)
        } else {
            val randomKey = ByteArray(KEY_SIZE_BYTES)
            SecureRandom().nextBytes(randomKey)
            val encodedKey = Base64.encodeToString(randomKey, Base64.NO_WRAP)
            securePrefs.edit().putString(KEY_DB_PASSPHRASE, encodedKey).commit()
            randomKey
        }
    }

    private fun createSecurePrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            KEY_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
