package com.mastercontrol.app.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PBKDF2-based PIN hashing for the app lock. No custom crypto: we use the
 * platform PBKDF2WithHmacSHA256 and a per-device random salt. The hashed PIN
 * is stored through [SecretStore] (itself AES-GCM under the Android Keystore).
 */
@Singleton
class PinHasher @Inject constructor() {

    private val random = SecureRandom()

    /** Returns `saltB64:hashB64`. */
    fun hash(pin: String, salt: ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }): String {
        val key = derive(pin, salt)
        return encode(salt) + ":" + encode(key)
    }

    /** Constant-time comparison against a stored hash. */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = decode(parts[0])
        val expected = decode(parts[1])
        val actual = derive(pin, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)

    companion object {
        private const val ITERATIONS = 150_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
    }
}
