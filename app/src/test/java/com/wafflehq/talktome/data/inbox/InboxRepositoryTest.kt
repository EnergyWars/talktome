package com.wafflehq.talktome.data.inbox

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.wafflehq.talktome.data.crypto.E2eIdentity
import com.wafflehq.talktome.data.db.InboxMessageDao
import com.wafflehq.talktome.data.db.InboxMessageEntity
import com.wafflehq.talktome.data.db.InboxMessageStatus
import com.wafflehq.talktome.data.db.MediatorNoteDao
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.db.PartnerEntity
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiErrorReason
import com.wafflehq.talktome.data.gemini.GeminiGenerateContentResult
import com.wafflehq.talktome.data.gemini.GeminiModel
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxItemDto
import com.wafflehq.talktome.data.network.MailboxItemOutcome
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.network.MailboxSubmissionDto
import com.wafflehq.talktome.data.network.MailboxSubmissionResponseDto
import com.wafflehq.talktome.data.network.OutgoingMessageEnvelope
import com.wafflehq.talktome.data.network.RejectionEnvelope
import com.wafflehq.talktome.data.profile.Profile
import com.wafflehq.talktome.data.profile.ProfileRepository
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import com.wafflehq.talktome.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Base64

class InboxRepositoryTest {

    private lateinit var inboxMessageDao: InboxMessageDao
    private lateinit var mediatorNoteDao: MediatorNoteDao
    private lateinit var profileRepository: ProfileRepository
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var geminiClient: GeminiClient
    private lateinit var partnerDao: PartnerDao
    private lateinit var mailboxApi: MailboxApi
    private lateinit var ownIdentity: E2eIdentity
    private lateinit var senderIdentity: E2eIdentity
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repository: InboxRepository

    private val item = MailboxItemDto(messageId = "m1", senderDeviceId = "sender-device", kind = "MESSAGE", ciphertext = "", createdAt = 1L)

    @Before
    fun setUp() {
        HybridConfig.register()
        inboxMessageDao = mockk(relaxed = true)
        mediatorNoteDao = mockk(relaxed = true)
        profileRepository = mockk()
        secureApiKeyStore = mockk()
        geminiClient = mockk()
        partnerDao = mockk()
        mailboxApi = mockk()
        ownIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))
        senderIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))

        settingsRepository = mockk()
        every { profileRepository.profile } returns flowOf(Profile.Empty.copy(filterText = "keine Vorwürfe"))
        every { mediatorNoteDao.observeByRole(any()) } returns flowOf(emptyList())
        every { settingsRepository.geminiModel } returns flowOf(GeminiModel.DEFAULT)

        repository = InboxRepository(
            inboxMessageDao,
            mediatorNoteDao,
            profileRepository,
            secureApiKeyStore,
            geminiClient,
            partnerDao,
            mailboxApi,
            ownIdentity,
            settingsRepository,
        )
    }

    private fun envelopeBytes(text: String = "Ich vermisse dich", senderMessageRef: Long = 7L) =
        Json.encodeToString(OutgoingMessageEnvelope.serializer(), OutgoingMessageEnvelope(senderMessageRef, text)).toByteArray()

    @Test
    fun `an unparsable envelope is treated as processed without side effects`() = runTest {
        val outcome = repository.handleIncoming("not json".toByteArray(), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.PROCESSED, outcome)
        coVerify(exactly = 0) { inboxMessageDao.upsert(any()) }
        coVerify(exactly = 0) { mailboxApi.submit(any(), any(), any(), any()) }
    }

    @Test
    fun `retries later without an api key`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns null

        val outcome = repository.handleIncoming(envelopeBytes(), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.RETRY_LATER, outcome)
    }

    @Test
    fun `retries later without a paired partner`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { partnerDao.get() } returns null

        val outcome = repository.handleIncoming(envelopeBytes(), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.RETRY_LATER, outcome)
    }

    @Test
    fun `an approved message is stored with feedback and never triggers a rejection submission`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { partnerDao.get() } returns PartnerEntity(displayName = "Robin", publicKey = senderIdentity.publicKeyBase64, remoteDeviceId = "sender-device")
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Success("ENTSCHEIDUNG: ZULASSEN\nDein Partner vermisst dich.")

        val outcome = repository.handleIncoming(envelopeBytes("Ich vermisse dich"), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.PROCESSED, outcome)
        coVerify {
            inboxMessageDao.upsert(
                match {
                    it.status == InboxMessageStatus.APPROVED &&
                        it.content == "Ich vermisse dich" &&
                        it.mediatorFeedback == "Dein Partner vermisst dich." &&
                        it.serverMessageId == "m1" &&
                        it.senderDeviceId == "sender-device"
                },
            )
        }
        coVerify(exactly = 0) { mailboxApi.submit(any(), any(), any(), any()) }
    }

    @Test
    fun `a rejected message leaves no trace and is sent back encrypted to the partner`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { partnerDao.get() } returns PartnerEntity(displayName = "Robin", publicKey = senderIdentity.publicKeyBase64, remoteDeviceId = "sender-device")
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Success("ENTSCHEIDUNG: ABLEHNEN\nDas triggert den Filter.")
        val submissionSlot = slot<MailboxSubmissionDto>()
        coEvery { mailboxApi.submit("https://server.example", "token", "sender-device", capture(submissionSlot)) } returns
            MailboxResult.Success(MailboxSubmissionResponseDto("rejection-1"))

        val outcome = repository.handleIncoming(envelopeBytes(senderMessageRef = 7L), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.PROCESSED, outcome)
        coVerify(exactly = 0) { inboxMessageDao.upsert(any()) }

        val decrypted = senderIdentity.decryptOwn(Base64.getDecoder().decode(submissionSlot.captured.ciphertext))
        val rejection = Json.decodeFromString(RejectionEnvelope.serializer(), String(decrypted, Charsets.UTF_8))
        assertEquals(7L, rejection.senderMessageRef)
        assertEquals("Das triggert den Filter.", rejection.feedback)
    }

    @Test
    fun `a malformed gatekeeper response defaults to a fail-safe rejection`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { partnerDao.get() } returns PartnerEntity(displayName = "Robin", publicKey = senderIdentity.publicKeyBase64, remoteDeviceId = "sender-device")
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Success("Klingt für mich in Ordnung.")
        coEvery { mailboxApi.submit(any(), any(), any(), any()) } returns MailboxResult.Success(MailboxSubmissionResponseDto("rejection-1"))

        repository.handleIncoming(envelopeBytes(), item, "https://server.example", "token", "me")

        coVerify(exactly = 0) { inboxMessageDao.upsert(any()) }
        coVerify { mailboxApi.submit(any(), any(), any(), any()) }
    }

    @Test
    fun `retries later when the gemini call fails`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { partnerDao.get() } returns PartnerEntity(displayName = "Robin", publicKey = senderIdentity.publicKeyBase64, remoteDeviceId = "sender-device")
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Error(GeminiErrorReason.NETWORK)

        val outcome = repository.handleIncoming(envelopeBytes(), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.RETRY_LATER, outcome)
    }

    @Test
    fun `retries later when sending the rejection back fails`() = runTest {
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { partnerDao.get() } returns PartnerEntity(displayName = "Robin", publicKey = senderIdentity.publicKeyBase64, remoteDeviceId = "sender-device")
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Success("ENTSCHEIDUNG: ABLEHNEN\nGrund")
        coEvery { mailboxApi.submit(any(), any(), any(), any()) } returns
            MailboxResult.Failure(com.wafflehq.talktome.data.network.MailboxErrorReason.NETWORK)

        val outcome = repository.handleIncoming(envelopeBytes(), item, "https://server.example", "token", "me")

        assertEquals(MailboxItemOutcome.RETRY_LATER, outcome)
    }
}
