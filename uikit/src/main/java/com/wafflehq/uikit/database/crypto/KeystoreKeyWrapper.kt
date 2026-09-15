package com.wafflehq.uikit.database.crypto

data class WrappedDek(val iv: ByteArray, val ciphertext: ByteArray)

interface KeystoreKeyWrapper {
    fun wrap(dek: ByteArray): WrappedDek
    fun unwrap(wrapped: WrappedDek): ByteArray
    fun deleteKey()
}
