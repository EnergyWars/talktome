package com.wafflehq.talktome.data.sync

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Base64

class MailboxSyncRepositoryTest {

    private lateinit var mailboxApi: MailboxApi
    private lateinit var deviceIdentityStore: DeviceIdentityStore
    private lateinit var partnerDao: PartnerDao
    private lateinit var ownIdentity: E2eIdentity
    private lateinit var peerIdentity: E2eIdentity
    private lateinit var inboxRepository: InboxRepository
    private lateinit var outgoingMessageRepository: OutgoingMessageRepository
    private lateinit var repository: MailboxSyncRepository

    private val identity = DeviceIdentity("me", "token", "https://server.example")

    @Before
    fun setUp() {
        HybridConfig.register()
        mailboxApi = mockk()
        deviceIdentityStore = mockk()
        partnerDao = mockk(relaxed = true)
        ownIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))
        peerIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))
        inboxRepository = mockk()
        outgoingMessageRepository = mockk()

        repository = MailboxSyncRepository(mailboxApi, deviceIdentityStore, partnerDao, ownIdentity, inboxRepository, outgoingMessageRepository)
    }

    private fun encryptedItem(kind: String, payload: ByteArray, messageId: String = "m1"): MailboxItemDto =
        MailboxItemDto(
            messageId = messageId,
            senderDeviceId = "peer",
            kind = kind,
            ciphertext = Base64.getEncoder().encodeToString(ownIdentity.encryptFor(ownIdentity.publicKeyBase64, payload)),
            createdAt = 1L,
        )

    @Test
    fun `sync reports missing identity without calling the mailbox`() = runTest {
        coEvery { deviceIdentityStore.getIdentity() } returns null

        val result = repository.sync()

        assertEquals(MailboxSyncResult.NoIdentity, result)
    }

    @Test
    fun `sync maps a pull failure`() = runTest {
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(identity.serverBaseUrl, identity.token) } returns MailboxResult.Failure(MailboxErrorReason.NETWORK)

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Error(MailboxErrorReason.NETWORK), result)
    }

    @Test
    fun `sync with no pending items reports zero processed`() = runTest {
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(identity.serverBaseUrl, identity.token) } returns MailboxResult.Success(emptyList())

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Synced(0), result)
    }

    @Test
    fun `a pairing ack item upserts the partner and is acknowledged`() = runTest {
        val ack = PairingAckPayload(deviceId = "peer-device", publicKey = peerIdentity.publicKeyBase64, displayName = "Robin")
        val payload = Json.encodeToString(PairingAckPayload.serializer(), ack).toByteArray()
        val item = encryptedItem(MailboxKind.PAIRING_ACK, payload)
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(any(), any()) } returns MailboxResult.Success(listOf(item))
        coEvery { mailboxApi.acknowledge(any(), any(), any()) } returns MailboxResult.Success(Unit)

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Synced(1), result)
        coVerify {
            partnerDao.upsert(
                match { it.displayName == "Robin" && it.remoteDeviceId == "peer-device" && it.publicKey == peerIdentity.publicKeyBase64 },
            )
        }
        coVerify { mailboxApi.acknowledge(identity.serverBaseUrl, identity.token, "m1") }
    }

    @Test
    fun `a message item is delegated to the inbox repository and acked when processed`() = runTest {
        val item = encryptedItem(MailboxKind.MESSAGE, "irrelevant".toByteArray())
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(any(), any()) } returns MailboxResult.Success(listOf(item))
        coEvery { inboxRepository.handleIncoming(any(), item, identity.serverBaseUrl, identity.token, identity.deviceId) } returns
            MailboxItemOutcome.PROCESSED
        coEvery { mailboxApi.acknowledge(any(), any(), any()) } returns MailboxResult.Success(Unit)

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Synced(1), result)
        coVerify { mailboxApi.acknowledge(identity.serverBaseUrl, identity.token, "m1") }
    }

    @Test
    fun `a message item that needs a retry is not acknowledged`() = runTest {
        val item = encryptedItem(MailboxKind.MESSAGE, "irrelevant".toByteArray())
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(any(), any()) } returns MailboxResult.Success(listOf(item))
        coEvery { inboxRepository.handleIncoming(any(), any(), any(), any(), any()) } returns MailboxItemOutcome.RETRY_LATER

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Synced(0), result)
        coVerify(exactly = 0) { mailboxApi.acknowledge(any(), any(), any()) }
    }

    @Test
    fun `a rejection item is delegated to the outgoing message repository`() = runTest {
        val item = encryptedItem(MailboxKind.REJECTION, "irrelevant".toByteArray())
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(any(), any()) } returns MailboxResult.Success(listOf(item))
        coEvery { outgoingMessageRepository.handleRejection(any()) } returns MailboxItemOutcome.PROCESSED
        coEvery { mailboxApi.acknowledge(any(), any(), any()) } returns MailboxResult.Success(Unit)

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Synced(1), result)
        coVerify { outgoingMessageRepository.handleRejection(any()) }
    }

    @Test
    fun `an item that cannot be decrypted is skipped and still acknowledged`() = runTest {
        val poisoned = MailboxItemDto(messageId = "m1", senderDeviceId = "peer", kind = MailboxKind.MESSAGE, ciphertext = "not-valid-base64-or-ciphertext", createdAt = 1L)
        coEvery { deviceIdentityStore.getIdentity() } returns identity
        coEvery { mailboxApi.pull(any(), any()) } returns MailboxResult.Success(listOf(poisoned))
        coEvery { mailboxApi.acknowledge(any(), any(), any()) } returns MailboxResult.Success(Unit)

        val result = repository.sync()

        assertEquals(MailboxSyncResult.Synced(1), result)
        coVerify(exactly = 0) { inboxRepository.handleIncoming(any(), any(), any(), any(), any()) }
        coVerify { mailboxApi.acknowledge(any(), any(), "m1") }
    }
}
