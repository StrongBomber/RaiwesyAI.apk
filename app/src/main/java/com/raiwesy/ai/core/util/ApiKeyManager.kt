package com.raiwesy.ai.core.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.raiwesy.ai.BuildConfig

/** App appearance / theme mode (independent from the OS setting when forced). */
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Secure storage for the API key and app preferences.
 *
 * The key is stored in [EncryptedSharedPreferences] (Android Keystore backed).
 * If the keystore is unavailable the manager degrades gracefully to plain
 * SharedPreferences instead of crashing.
 *
 * Resolution order for the effective key:
 *   1. user-provided key (Settings screen, encrypted on device)
 *   2. build-time key (local.properties / GitHub Secrets -> BuildConfig)
 *   3. bundled default key (app ships ready-to-use; can be changed in Settings)
 */
class ApiKeyManager(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    /** User-provided key from the Settings screen (null when absent). */
    fun storedKey(): String? =
        prefs.getString(KEY_API, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    /**
     * The key actually used for API calls.
     * Priority: user key (device) > build-time key > bundled default key.
     */
    fun effectiveKey(): String? =
        storedKey()
            ?: BuildConfig.NVIDIA_API_KEY.trim().takeIf { it.isNotEmpty() }
            ?: DEFAULT_API_KEY

    /** True when any non-user key is available (build-time or bundled). */
    fun hasEmbeddedKey(): Boolean =
        BuildConfig.NVIDIA_API_KEY.trim().isNotEmpty() || DEFAULT_API_KEY.isNotEmpty()

    fun appearanceMode(): AppearanceMode =
        runCatching {
            AppearanceMode.valueOf(
                prefs.getString(KEY_APPEARANCE, AppearanceMode.SYSTEM.name)
                    ?: AppearanceMode.SYSTEM.name
            )
        }.getOrDefault(AppearanceMode.SYSTEM)

    fun setAppearanceMode(mode: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE, mode.name).apply()
    }

    // ---- crash marker (for the friendly "app restarted" dialog) ----

    fun lastCrashTimestamp(): Long = prefs.getLong(KEY_LAST_CRASH, 0L)

    fun markCrashed() {
        prefs.edit().putLong(KEY_LAST_CRASH, System.currentTimeMillis()).apply()
    }

    fun clearCrashMark() {
        prefs.edit().remove(KEY_LAST_CRASH).apply()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            Log.w(TAG, "EncryptedSharedPreferences kullanılamıyor, normal depolamaya geçiliyor", t)
            context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val TAG = "ApiKeyManager"
        const val PREFERENCES_FILE = "raiwesy_prefs"
        const val KEY_API = "nvidia_api_key"
        const val KEY_APPEARANCE = "appearance_mode"
        const val KEY_LAST_CRASH = "last_crash"

        /**
         * Varsayılan (otomatik doldurulan) API anahtarı. Uygulama kurulumdan
         * hemen sonra çalışır; kullanıcı Ayarlar'dan kendi anahtarıyla
         * değiştirebilir.
         */
        const val DEFAULT_API_KEY =
            "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"
    }
}
