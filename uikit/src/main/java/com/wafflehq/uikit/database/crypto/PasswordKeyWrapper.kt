package com.wafflehq.uikit.database.crypto

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordWrappedDek(
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val iterations: Int,
)

class PasswordKeyWrapper {

    fun wrap(dek: ByteArray, password: CharArray): PasswordWrappedDek {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)
        val encrypted = AesGcmCipher.encrypt(key, dek)
        return PasswordWrappedDek(salt = salt, iv = encrypted.iv, ciphertext = encrypted.ciphertext, iterations = ITERATIONS)
    }

    fun unwrap(wrapped: PasswordWrappedDek, password: CharArray): ByteArray {
        val key = deriveKey(password, wrapped.salt, wrapped.iterations)
        return AesGcmCipher.decrypt(key, wrapped.iv, wrapped.ciphertext)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 600_000
        private const val SALT_LENGTH_BYTES = 16
        private const val KEY_LENGTH_BITS = 256
    }
}
