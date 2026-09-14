package com.mastercontrol.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

object SecretAliases {
    const val TELEGRAM_API_HASH = "telegram_api_hash_v1"
    const val TELEGRAM_API_ID = "telegram_api_id_v1"
    const val TD_DATABASE_KEY = "td_database_encryption_key_v1"
    const val APP_LOCK_PIN = "app_lock_pin_hash_v1"
}

/**
 * Encrypted key/value store for small secrets. Values are AES-GCM encrypted
 * with an Android Keystore key; ciphertext lives in ordinary SharedPreferences
 * (there is nothing sensitive left in plaintext).
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: AndroidKeyStoreCipher,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(alias: String, plaintext: String) {
        val encrypted = cipher.encrypt(alias, plaintext.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(alias, encrypted).apply()
    }

    fun read(alias: String): String? {
        val encrypted = prefs.getString(alias, null) ?: return null
        return try {
            String(cipher.decrypt(alias, encrypted), StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            // Key rotation/keystore reset invalidates old ciphertext; treat as absent.
            prefs.edit().remove(alias).apply()
            null
        }
    }

    fun contains(alias: String): Boolean = prefs.contains(alias)

    fun clear(alias: String) {
        prefs.edit().remove(alias).apply()
        cipher.deleteKey(alias)
    }

    fun saveBytes(alias: String, bytes: ByteArray) {
        save(alias, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    fun readBytes(alias: String): ByteArray? =
        read(alias)?.let { Base64.decode(it, Base64.NO_WRAP) }

    companion object {
        private const val PREFS_NAME = "master_control_secrets"
    }
}

/** Provides the stable TDLib database encryption key (base64 string). */
@Singleton
class TdlibEncryptionKeyProvider @Inject constructor(
    private val store: SecretStore,
) {
    fun getOrCreate(): String {
        store.read(SecretAliases.TD_DATABASE_KEY)?.let { return it }
        // 32 random bytes encoded as a Base64 string; tdjson accepts a base64
        // string for its `bytes` database_encryption_key field.
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val b64 = Base64.encodeToString(key, Base64.NO_WRAP)
        store.save(SecretAliases.TD_DATABASE_KEY, b64)
        return b64
    }
}
