package com.wafflehq.talktome.data.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
data class InvitePayload(
    val serverBaseUrl: String,
    val inviterDeviceId: String,
    val inviterPublicKey: String,
    val inviterDisplayName: String,
)

@Serializable
data class PairingAckPayload(
    val deviceId: String,
    val publicKey: String,
    val displayName: String,
)

object InviteCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: InvitePayload): String {
        val bytes = json.encodeToString(InvitePayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(code: String): InvitePayload? = try {
        val bytes = Base64.getUrlDecoder().decode(code.trim())
        json.decodeFromString(InvitePayload.serializer(), String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}
