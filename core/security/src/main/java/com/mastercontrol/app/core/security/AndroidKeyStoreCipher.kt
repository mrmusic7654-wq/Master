package com.mastercontrol.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Thin AES-256-GCM wrapper around the Android Keystore. Keys never leave the
 * secure hardware/software keystore; the app only ever handles ciphertext.
 */
class AndroidKeyStoreCipher(
    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) },
) {

    fun ensureKey(alias: String) {
        if (keystore.containsAlias(alias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generator.generateKey()
    }

    /** Encrypts [plaintext] and returns a Base64 payload of `iv || ciphertext`. */
    fun encrypt(alias: String, plaintext: ByteArray): String {
        ensureKey(alias)
        val key = keystore.getKey(alias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        val payload = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(payload, 0)
        ciphertext.copyInto(payload, iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Decrypts a payload produced by [encrypt]. Throws on tampering (GCM tag). */
    fun decrypt(alias: String, payloadB64: String): ByteArray {
        val payload = Base64.decode(payloadB64, Base64.NO_WRAP)
        require(payload.size > 12) { "Payload too short" }
        val key = keystore.getKey(alias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload, 0, 12))
        return cipher.doFinal(payload, 12, payload.size - 12)
    }

    fun deleteKey(alias: String) {
        if (keystore.containsAlias(alias)) keystore.deleteEntry(alias)
    }

    fun hasKey(alias: String): Boolean = keystore.containsAlias(alias)

    /** Generates [size] cryptographically random bytes (never leaves the device). */
    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
