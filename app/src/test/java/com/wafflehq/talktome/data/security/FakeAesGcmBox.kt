package com.wafflehq.talktome.data.security

import com.wafflehq.uikit.security.AesGcmBox
import com.wafflehq.uikit.security.AesGcmPayload
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Plain-JVM AES/GCM implementation used in tests instead of the real Android-Keystore-backed
 * box, which requires a device/emulator. Uses genuine AES/GCM so encrypt/decrypt round trips and
 * tamper detection are exercised for real, not mocked away.
 */
class FakeAesGcmBox(seed: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }) : AesGcmBox {

    private val key = SecretKeySpec(seed, "AES")

    override fun encrypt(plaintext: ByteArray): AesGcmPayload {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return AesGcmPayload(iv = iv, ciphertext = cipher.doFinal(plaintext))
    }

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
