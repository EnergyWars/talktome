package com.wafflehq.talktome.data.pairing

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.wafflehq.talktome.data.crypto.DeviceIdentity
import com.wafflehq.talktome.data.crypto.DeviceIdentityStore
import com.wafflehq.talktome.data.crypto.E2eIdentity
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.db.PartnerEntity
import com.wafflehq.talktome.data.network.DeviceRegistrationDto
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxErrorReason
import com.wafflehq.talktome.data.network.MailboxKind
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.network.MailboxSubmissionDto
import com.wafflehq.talktome.data.network.MailboxSubmissionResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Base64

class PairingRepositoryTest {

    private lateinit var mailboxApi: MailboxApi
    private lateinit var deviceIdentityStore: DeviceIdentityStore
    private lateinit var partnerDao: PartnerDao
    private lateinit var acceptorIdentity: E2eIdentity
    private lateinit var inviterIdentity: E2eIdentity
    private lateinit var repository: PairingRepository

    @Before
    fun setUp() {
        HybridConfig.register()
        mailboxApi = mockk()
        deviceIdentityStore = mockk()
        partnerDao = mockk(relaxed = true)
        acceptorIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))
        inviterIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))
        repository = PairingRepository(mailboxApi, deviceIdentityStore, acceptorIdentity, partnerDao)
    }

    @Test
    fun `ensureDeviceIdentity returns the existing identity without registering`() = runTest {
        val existing = DeviceIdentity("device-1", "token-1", "https://server.example")
        coEvery { deviceIdentityStore.getIdentity() } returns existing

        val result = repository.ensureDeviceIdentity("https://server.example")

        assertEquals(PairingResult.Success(existing), result)
        coVerify(exactly = 0) { mailboxApi.registerDevice(any()) }
    }

    @Test
    fun `ensureDeviceIdentity registers and persists a new identity when none exists`() = runTest {
        coEvery { deviceIdentityStore.getIdentity() } returns null
        coEvery { mailboxApi.registerDevice("https://server.example") } returns
            MailboxResult.Success(DeviceRegistrationDto("device-2", "token-2"))
        coEvery { deviceIdentityStore.saveIdentity(any()) } returns Unit

        val result = repository.ensureDeviceIdentity("https://server.example")

        assertEquals(PairingResult.Success(DeviceIdentity("device-2", "token-2", "https://server.example")), result)
        coVerify { deviceIdentityStore.saveIdentity(DeviceIdentity("device-2", "token-2", "https://server.example")) }
    }

    @Test
    fun `ensureDeviceIdentity maps a network failure`() = runTest {
        coEvery { deviceIdentityStore.getIdentity() } returns null
        coEvery { mailboxApi.registerDevice(any()) } returns MailboxResult.Failure(MailboxErrorReason.NETWORK)

        val result = repository.ensureDeviceIdentity("https://server.example")

        assertEquals(PairingResult.Failure(PairingErrorReason.NETWORK), result)
    }

    @Test
    fun `createInvite embeds the own device id and public key`() = runTest {
        coEvery { deviceIdentityStore.getIdentity() } returns DeviceIdentity("device-1", "token-1", "https://server.example")

        val result = repository.createInvite("https://server.example", "Alex") as PairingResult.Success

        val decoded = InviteCodec.decode(result.value)!!
        assertEquals("device-1", decoded.inviterDeviceId)
        assertEquals(acceptorIdentity.publicKeyBase64, decoded.inviterPublicKey)
        assertEquals("Alex", decoded.inviterDisplayName)
        assertEquals("https://server.example", decoded.serverBaseUrl)
    }

    @Test
    fun `acceptInvite rejects an unparsable code without any network call`() = runTest {
        val result = repository.acceptInvite("not-a-valid-code!!", "Sam")

        assertEquals(PairingResult.Failure(PairingErrorReason.INVALID_CODE), result)
        coVerify(exactly = 0) { mailboxApi.submit(any(), any(), any(), any()) }
    }

    @Test
    fun `acceptInvite stores the partner and sends an encrypted ack only the inviter can read`() = runTest {
        val invite = InvitePayload(
            serverBaseUrl = "https://server.example",
            inviterDeviceId = "inviter-device",
            inviterPublicKey = inviterIdentity.publicKeyBase64,
            inviterDisplayName = "Robin",
        )
        val code = InviteCodec.encode(invite)
        coEvery { deviceIdentityStore.getIdentity() } returns DeviceIdentity("acceptor-device", "acceptor-token", "https://server.example")
        val submissionSlot = slot<MailboxSubmissionDto>()
        coEvery { mailboxApi.submit("https://server.example", "acceptor-token", "inviter-device", capture(submissionSlot)) } returns
            MailboxResult.Success(MailboxSubmissionResponseDto("message-1"))

        val result = repository.acceptInvite(code, "Sam")

        assertEquals(PairingResult.Success(Unit), result)
        coVerify {
            partnerDao.upsert(
                PartnerEntity(
                    displayName = "Robin",
                    publicKey = inviterIdentity.publicKeyBase64,
                    pairedAt = any(),
                    remoteDeviceId = "inviter-device",
                    serverBaseUrl = "https://server.example",
                ),
            )
        }

        val submission = submissionSlot.captured
        assertEquals(MailboxKind.PAIRING_ACK, submission.kind)
        assertEquals("acceptor-device", submission.senderDeviceId)
        val decryptedAck = inviterIdentity.decryptOwn(Base64.getDecoder().decode(submission.ciphertext))
        val ackPayload = Json.decodeFromString(PairingAckPayload.serializer(), String(decryptedAck, Charsets.UTF_8))
        assertEquals("acceptor-device", ackPayload.deviceId)
        assertEquals("Sam", ackPayload.displayName)
        assertEquals(acceptorIdentity.publicKeyBase64, ackPayload.publicKey)
    }

    @Test
    fun `acceptInvite maps a submit failure without storing a partner`() = runTest {
        val invite = InvitePayload("https://server.example", "inviter-device", inviterIdentity.publicKeyBase64, "Robin")
        val code = InviteCodec.encode(invite)
        coEvery { deviceIdentityStore.getIdentity() } returns DeviceIdentity("acceptor-device", "acceptor-token", "https://server.example")
        coEvery { mailboxApi.submit(any(), any(), any(), any()) } returns MailboxResult.Failure(MailboxErrorReason.NOT_FOUND)

        val result = repository.acceptInvite(code, "Sam")

        assertEquals(PairingResult.Failure(PairingErrorReason.SERVER_ERROR), result)
        coVerify(exactly = 0) { partnerDao.upsert(any()) }
    }

    @Test
    fun `unpair clears the stored partner`() = runTest {
        repository.unpair()

        coVerify { partnerDao.deleteAll() }
    }

    @Test
    fun `partner exposes the dao flow`() = runTest {
        val partnerEntity = PartnerEntity(displayName = "Robin")
        every { partnerDao.observe() } returns flowOf(partnerEntity)
        val freshRepository = PairingRepository(mailboxApi, deviceIdentityStore, acceptorIdentity, partnerDao)

        assertEquals(partnerEntity, freshRepository.partner.first())
    }
}
