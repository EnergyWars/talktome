package com.wafflehq.talktome.data.pairing

import com.wafflehq.talktome.data.crypto.DeviceIdentity
import com.wafflehq.talktome.data.crypto.DeviceIdentityStore
import com.wafflehq.talktome.data.crypto.E2eIdentity
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.db.PartnerEntity
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxErrorReason
import com.wafflehq.talktome.data.network.MailboxKind
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.network.MailboxSubmissionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

enum class PairingErrorReason {
    NETWORK,
    INVALID_CODE,
    SERVER_ERROR,
}

sealed interface PairingResult<out T> {
    data class Success<out T>(val value: T) : PairingResult<T>
    data class Failure(val reason: PairingErrorReason) : PairingResult<Nothing>
}

@Singleton
class PairingRepository @Inject constructor(
    private val mailboxApi: MailboxApi,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val e2eIdentity: E2eIdentity,
    private val partnerDao: PartnerDao,
) {
    val partner: Flow<PartnerEntity?> = partnerDao.observe()

    suspend fun ensureDeviceIdentity(serverBaseUrl: String): PairingResult<DeviceIdentity> {
        deviceIdentityStore.getIdentity()?.let { return PairingResult.Success(it) }
        return when (val result = mailboxApi.registerDevice(serverBaseUrl)) {
            is MailboxResult.Success -> {
                val identity = DeviceIdentity(result.value.deviceId, result.value.token, serverBaseUrl)
                deviceIdentityStore.saveIdentity(identity)
                PairingResult.Success(identity)
            }
            is MailboxResult.Failure -> PairingResult.Failure(result.reason.toPairingReason())
        }
    }

    suspend fun createInvite(serverBaseUrl: String, displayName: String): PairingResult<String> {
        val identity = when (val result = ensureDeviceIdentity(serverBaseUrl)) {
            is PairingResult.Success -> result.value
            is PairingResult.Failure -> return result
        }
        val payload = InvitePayload(
            serverBaseUrl = serverBaseUrl,
            inviterDeviceId = identity.deviceId,
            inviterPublicKey = e2eIdentity.publicKeyBase64,
            inviterDisplayName = displayName,
        )
        return PairingResult.Success(InviteCodec.encode(payload))
    }

    suspend fun acceptInvite(code: String, myDisplayName: String): PairingResult<Unit> {
        val invite = InviteCodec.decode(code) ?: return PairingResult.Failure(PairingErrorReason.INVALID_CODE)
        val identity = when (val result = ensureDeviceIdentity(invite.serverBaseUrl)) {
            is PairingResult.Success -> result.value
            is PairingResult.Failure -> return result
        }

        val ack = PairingAckPayload(deviceId = identity.deviceId, publicKey = e2eIdentity.publicKeyBase64, displayName = myDisplayName)
        val ackJson = Json.encodeToString(PairingAckPayload.serializer(), ack)
        val ciphertext = e2eIdentity.encryptFor(invite.inviterPublicKey, ackJson.toByteArray(Charsets.UTF_8))
        val submission = MailboxSubmissionDto(
            senderDeviceId = identity.deviceId,
            kind = MailboxKind.PAIRING_ACK,
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )

        return when (val result = mailboxApi.submit(invite.serverBaseUrl, identity.token, invite.inviterDeviceId, submission)) {
            is MailboxResult.Success -> {
                partnerDao.upsert(
                    PartnerEntity(
                        displayName = invite.inviterDisplayName,
                        publicKey = invite.inviterPublicKey,
                        pairedAt = System.currentTimeMillis(),
                        remoteDeviceId = invite.inviterDeviceId,
                        serverBaseUrl = invite.serverBaseUrl,
                    ),
                )
                PairingResult.Success(Unit)
            }
            is MailboxResult.Failure -> PairingResult.Failure(result.reason.toPairingReason())
        }
    }

    suspend fun unpair() {
        partnerDao.deleteAll()
    }

    private fun MailboxErrorReason.toPairingReason(): PairingErrorReason = when (this) {
        MailboxErrorReason.NETWORK -> PairingErrorReason.NETWORK
        else -> PairingErrorReason.SERVER_ERROR
    }
}
