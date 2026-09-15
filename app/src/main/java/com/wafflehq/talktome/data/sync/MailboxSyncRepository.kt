package com.wafflehq.talktome.data.sync

import com.wafflehq.talktome.data.crypto.DeviceIdentity
import com.wafflehq.talktome.data.crypto.DeviceIdentityStore
import com.wafflehq.talktome.data.crypto.E2eIdentity
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.db.PartnerEntity
import com.wafflehq.talktome.data.inbox.InboxRepository
import com.wafflehq.talktome.data.negotiation.OutgoingMessageRepository
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxErrorReason
import com.wafflehq.talktome.data.network.MailboxItemDto
import com.wafflehq.talktome.data.network.MailboxItemOutcome
import com.wafflehq.talktome.data.network.MailboxKind
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.pairing.PairingAckPayload
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MailboxSyncResult {
    data class Synced(val processedCount: Int) : MailboxSyncResult
    data object NoIdentity : MailboxSyncResult
    data class Error(val reason: MailboxErrorReason) : MailboxSyncResult
}

@Singleton
class MailboxSyncRepository @Inject constructor(
    private val mailboxApi: MailboxApi,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val partnerDao: PartnerDao,
    private val e2eIdentity: E2eIdentity,
    private val inboxRepository: InboxRepository,
    private val outgoingMessageRepository: OutgoingMessageRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sync(): MailboxSyncResult {
        val identity = deviceIdentityStore.getIdentity() ?: return MailboxSyncResult.NoIdentity
        return when (val pulled = mailboxApi.pull(identity.serverBaseUrl, identity.token)) {
            is MailboxResult.Failure -> MailboxSyncResult.Error(pulled.reason)
            is MailboxResult.Success -> {
                var processed = 0
                pulled.value.forEach { item -> if (processItem(item, identity)) processed++ }
                MailboxSyncResult.Synced(processed)
            }
        }
    }

    private suspend fun processItem(item: MailboxItemDto, identity: DeviceIdentity): Boolean {
        val plaintext = decrypt(item.ciphertext)
        val outcome = if (plaintext == null) {
            MailboxItemOutcome.PROCESSED
        } else {
            when (item.kind) {
                MailboxKind.PAIRING_ACK -> handlePairingAck(plaintext, identity.serverBaseUrl)
                MailboxKind.MESSAGE -> inboxRepository.handleIncoming(plaintext, item, identity.serverBaseUrl, identity.token, identity.deviceId)
                MailboxKind.REJECTION -> outgoingMessageRepository.handleRejection(plaintext)
                else -> MailboxItemOutcome.PROCESSED
            }
        }
        if (outcome == MailboxItemOutcome.RETRY_LATER) return false
        mailboxApi.acknowledge(identity.serverBaseUrl, identity.token, item.messageId)
        return true
    }

    private fun decrypt(ciphertextBase64: String): ByteArray? = try {
        e2eIdentity.decryptOwn(Base64.getDecoder().decode(ciphertextBase64))
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private suspend fun handlePairingAck(plaintext: ByteArray, serverBaseUrl: String): MailboxItemOutcome = try {
        val ack = json.decodeFromString(PairingAckPayload.serializer(), String(plaintext, Charsets.UTF_8))
        partnerDao.upsert(
            PartnerEntity(
                displayName = ack.displayName,
                publicKey = ack.publicKey,
                remoteDeviceId = ack.deviceId,
                serverBaseUrl = serverBaseUrl,
                pairedAt = System.currentTimeMillis(),
            ),
        )
        MailboxItemOutcome.PROCESSED
    } catch (e: Exception) {
        MailboxItemOutcome.PROCESSED
    }
}
