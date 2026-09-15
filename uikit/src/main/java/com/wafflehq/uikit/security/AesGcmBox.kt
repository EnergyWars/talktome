package com.wafflehq.uikit.security

data class AesGcmPayload(val iv: ByteArray, val ciphertext: ByteArray)

interface AesGcmBox {
    fun encrypt(plaintext: ByteArray): AesGcmPayload
    fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray
}
