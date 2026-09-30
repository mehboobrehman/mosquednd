package com.mosquednd.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.concurrent.ConcurrentHashMap

object EncryptedPrefsManager {
    private const val ENCRYPTED_PREFIX = "encrypted_"

    /**
     * Cache of created EncryptedSharedPreferences instances keyed by logical name.
     * Each call to EncryptedSharedPreferences.create() performs MasterKey derivation,
     * file I/O, and cipher initialization (~50-100ms). Caching avoids redundant work
     * when both MosqueDNDApp.onCreate() and BootReceiver.onReceive() initialise the
     * same encrypted pref files.
     */
    private val cache = ConcurrentHashMap<String, SharedPreferences>()

    fun getEncryptedPrefs(context: Context, name: String): SharedPreferences {
        return cache.getOrPut(name) {
            val fileName = ENCRYPTED_PREFIX + name
            EncryptedSharedPreferences.create(
                fileName,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    // Intentionally public for future use (e.g., non-sensitive prefs with custom name)
    fun getRegularPrefs(context: Context, name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
