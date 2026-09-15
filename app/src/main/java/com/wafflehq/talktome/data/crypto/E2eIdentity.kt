package com.wafflehq.talktome.data.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import java.util.Base64

class E2eIdentity(private val keysetHandle: KeysetHandle) {

    val publicKeyBase64: String by lazy {
        val bytes = TinkProtoKeysetFormat.serializeKeysetWithoutSecret(keysetHandle.publicKeysetHandle)
        Base64.getEncoder().encodeToString(bytes)
    }

    fun encryptFor(recipientPublicKeyBase64: String, plaintext: ByteArray): ByteArray {
        val recipientHandle = TinkProtoKeysetFormat.parseKeysetWithoutSecret(
            Base64.getDecoder().decode(recipientPublicKeyBase64),
        )
        return recipientHandle.getPrimitive(HybridEncrypt::class.java).encrypt(plaintext, CONTEXT_INFO)
    }

    fun decryptOwn(ciphertext: ByteArray): ByteArray =
        keysetHandle.getPrimitive(HybridDecrypt::class.java).decrypt(ciphertext, CONTEXT_INFO)

    companion object {
        const val HYBRID_KEY_TEMPLATE_NAME = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
        val CONTEXT_INFO: ByteArray = "talktome-e2e-v1".toByteArray(Charsets.UTF_8)
    }
}
