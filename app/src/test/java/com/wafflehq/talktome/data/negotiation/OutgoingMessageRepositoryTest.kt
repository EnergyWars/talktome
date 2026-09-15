package com.wafflehq.talktome.data.negotiation

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.wafflehq.talktome.data.crypto.DeviceIdentity
import com.wafflehq.talktome.data.crypto.DeviceIdentityStore
import com.wafflehq.talktome.data.crypto.E2eIdentity
import com.wafflehq.talktome.data.db.MediatorAgentRole
import com.wafflehq.talktome.data.db.MediatorNoteDao
import com.wafflehq.talktome.data.db.MediatorNoteEntity
import com.wafflehq.talktome.data.db.NegotiationTurnDao
import com.wafflehq.talktome.data.db.NegotiationTurnEntity
import com.wafflehq.talktome.data.db.NegotiationTurnSender
import com.wafflehq.talktome.data.db.OutgoingMessageDao
import com.wafflehq.talktome.data.db.OutgoingMessageEntity
import com.wafflehq.talktome.data.db.OutgoingMessageStatus
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.db.PartnerEntity
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiErrorReason
import com.wafflehq.talktome.data.gemini.GeminiGenerateContentResult
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxErrorReason
import com.wafflehq.talktome.data.network.MailboxItemOutcome
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.network.MailboxSubmissionResponseDto
import com.wafflehq.talktome.data.network.OutgoingMessageEnvelope
import com.wafflehq.talktome.data.network.RejectionEnvelope
import com.wafflehq.talktome.data.profile.Profile
import com.wafflehq.talktome.data.profile.ProfileRepository
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.secondArg
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class OutgoingMessageRepositoryTest {

    private lateinit var outgoingMessageDao: OutgoingMessageDao
    private lateinit var negotiationTurnDao: NegotiationTurnDao
    private lateinit var mediatorNoteDao: MediatorNoteDao
    private lateinit var profileRepository: ProfileRepository
    private lateinit var secureApiKeyStore: SecureApiKeyStore
    private lateinit var geminiClient: GeminiClient
    private lateinit var partnerDao: PartnerDao
    private lateinit var mailboxApi: MailboxApi
    private lateinit var deviceIdentityStore: DeviceIdentityStore
    private lateinit var senderIdentity: E2eIdentity
    private lateinit var recipientIdentity: E2eIdentity
    private lateinit var repository: OutgoingMessageRepository

    @Before
    fun setUp() {
        HybridConfig.register()
        outgoingMessageDao = mockk(relaxed = true)
        negotiationTurnDao = mockk(relaxed = true)
        mediatorNoteDao = mockk(relaxed = true)
        profileRepository = mockk()
        secureApiKeyStore = mockk()
        geminiClient = mockk()
        partnerDao = mockk()
        mailboxApi = mockk()
        deviceIdentityStore = mockk()
        senderIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))
        recipientIdentity = E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))

        every { profileRepository.profile } returns flowOf(Profile.Empty)
        every { mediatorNoteDao.observeByRole(any()) } returns flowOf(emptyList())
        every { negotiationTurnDao.observeForMessage(any()) } returns flowOf(emptyList())

        repository = OutgoingMessageRepository(
            outgoingMessageDao,
            negotiationTurnDao,
            mediatorNoteDao,
            profileRepository,
            secureApiKeyStore,
            geminiClient,
            partnerDao,
            mailboxApi,
            deviceIdentityStore,
            senderIdentity,
        )
    }

    private fun draft(id: Long = 1L, status: OutgoingMessageStatus = OutgoingMessageStatus.DRAFT, rejectionCount: Int = 0) =
        OutgoingMessageEntity(id = id, draftText = "Ich wünsche mir mehr Zeit zu zweit", status = status, createdAt = 1L, updatedAt = 1L, rejectionCount = rejectionCount)

    @Test
    fun `startDraft persists a DRAFT entity and returns its id`() = runTest {
        coEvery { outgoingMessageDao.upsert(any()) } returns 42L

        val id = repository.startDraft("Hallo")

        assertEquals(42L, id)
        coVerify {
            outgoingMessageDao.upsert(
                match { it.draftText == "Hallo" && it.status == OutgoingMessageStatus.DRAFT },
            )
        }
    }

    @Test
    fun `startNegotiation fails fast without an api key and makes no gemini calls`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns null

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_API_KEY), result)
        coVerify(exactly = 0) { geminiClient.generateContent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `startNegotiation is a no-op once the message already left DRAFT`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft(status = OutgoingMessageStatus.NEGOTIATING)

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Success, result)
        coVerify(exactly = 0) { secureApiKeyStore.getApiKey() }
    }

    @Test
    fun `startNegotiation runs all four friend opinions and a mediator summary`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } answers {
            val systemInstruction = secondArg<String>()
            val text = when {
                systemInstruction.contains("neutraler Vermittler. Eine Person will") -> "Zusammenfassung"
                else -> "Meinung"
            }
            GeminiGenerateContentResult.Success(text)
        }

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Success, result)
        coVerify(exactly = 5) { geminiClient.generateContent(any(), any(), any(), any(), any()) }
        coVerify {
            outgoingMessageDao.upsert(
                match { it.status == OutgoingMessageStatus.NEGOTIATING && it.friendOpinionsJson?.contains("Meinung") == true },
            )
        }
        coVerify(exactly = 1) {
            negotiationTurnDao.insert(match { it.sender == NegotiationTurnSender.USER })
        }
        coVerify(exactly = 1) {
            negotiationTurnDao.insert(match { it.sender == NegotiationTurnSender.MEDIATOR && it.text == "Zusammenfassung" })
        }
    }

    @Test
    fun `startNegotiation tolerates some friend opinions failing`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        var callIndex = 0
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } answers {
            val systemInstruction = secondArg<String>()
            when {
                systemInstruction.contains("neutraler Vermittler. Eine Person will") -> GeminiGenerateContentResult.Success("Zusammenfassung")
                else -> {
                    callIndex++
                    if (callIndex <= 2) {
                        GeminiGenerateContentResult.Error(com.wafflehq.talktome.data.gemini.GeminiErrorReason.NETWORK)
                    } else {
                        GeminiGenerateContentResult.Success("Meinung $callIndex")
                    }
                }
            }
        }

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Success, result)
    }

    @Test
    fun `startNegotiation fails when every friend opinion call fails`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Error(com.wafflehq.talktome.data.gemini.GeminiErrorReason.NETWORK)

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.NETWORK), result)
        coVerify(exactly = 0) { negotiationTurnDao.insert(any()) }
    }

    @Test
    fun `startNegotiation prefers service unavailable over network when friend opinions fail for mixed reasons`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        var callIndex = 0
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } answers {
            callIndex++
            val reason = if (callIndex == 1) GeminiErrorReason.SERVICE_UNAVAILABLE else GeminiErrorReason.NETWORK
            GeminiGenerateContentResult.Error(reason)
        }

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.SERVICE_UNAVAILABLE), result)
    }

    @Test
    fun `startNegotiation reports an invalid api key instead of a generic network error`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Error(GeminiErrorReason.INVALID_API_KEY)

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.INVALID_API_KEY), result)
    }

    @Test
    fun `startNegotiation reports service unavailable when gemini is overloaded`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Error(GeminiErrorReason.SERVICE_UNAVAILABLE)

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.SERVICE_UNAVAILABLE), result)
    }

    @Test
    fun `startNegotiation reports blocked by safety filter when the mediator summary is blocked`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } answers {
            val systemInstruction = secondArg<String>()
            if (systemInstruction.contains("neutraler Vermittler. Eine Person will")) {
                GeminiGenerateContentResult.Error(GeminiErrorReason.BLOCKED_BY_SAFETY_FILTER)
            } else {
                GeminiGenerateContentResult.Success("Meinung")
            }
        }

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.BLOCKED_BY_SAFETY_FILTER), result)
    }

    @Test
    fun `startNegotiation reports an invalid api key when the mediator summary call is rejected`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } answers {
            val systemInstruction = secondArg<String>()
            if (systemInstruction.contains("neutraler Vermittler. Eine Person will")) {
                GeminiGenerateContentResult.Error(GeminiErrorReason.INVALID_API_KEY)
            } else {
                GeminiGenerateContentResult.Success("Meinung")
            }
        }

        val result = repository.startNegotiation(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.INVALID_API_KEY), result)
    }

    @Test
    fun `sendReply stores both turns and updates the draft text`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft(status = OutgoingMessageStatus.NEGOTIATING)
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { negotiationTurnDao.getForMessage(1L) } returns emptyList()
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns GeminiGenerateContentResult.Success("Antwort")

        val result = repository.sendReply(1L, "Neue Version des Textes")

        assertEquals(OutgoingActionResult.Success, result)
        coVerify { outgoingMessageDao.upsert(match { it.draftText == "Neue Version des Textes" }) }
        coVerify { negotiationTurnDao.insert(match { it.sender == NegotiationTurnSender.USER && it.text == "Neue Version des Textes" }) }
        coVerify { negotiationTurnDao.insert(match { it.sender == NegotiationTurnSender.MEDIATOR && it.text == "Antwort" }) }
    }

    @Test
    fun `sendReply passes prior turns as history`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft(status = OutgoingMessageStatus.NEGOTIATING)
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { negotiationTurnDao.getForMessage(1L) } returns listOf(
            NegotiationTurnEntity(outgoingMessageId = 1L, sender = NegotiationTurnSender.USER, text = "erste Version", createdAt = 1L),
            NegotiationTurnEntity(outgoingMessageId = 1L, sender = NegotiationTurnSender.MEDIATOR, text = "erstes Feedback", createdAt = 2L),
        )
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns GeminiGenerateContentResult.Success("Antwort")

        repository.sendReply(1L, "zweite Version")

        coVerify {
            geminiClient.generateContent(
                "key",
                any(),
                any(),
                any(),
                match { history -> history.size == 2 && history[0].text == "erste Version" },
            )
        }
    }

    @Test
    fun `sendReply reports an invalid api key instead of a generic network error`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft(status = OutgoingMessageStatus.NEGOTIATING)
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { negotiationTurnDao.getForMessage(1L) } returns emptyList()
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns
            GeminiGenerateContentResult.Error(GeminiErrorReason.INVALID_API_KEY)

        val result = repository.sendReply(1L, "Neue Version des Textes")

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.INVALID_API_KEY), result)
    }

    @Test
    fun `send fails without a paired partner`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { partnerDao.get() } returns null
        coEvery { deviceIdentityStore.getIdentity() } returns DeviceIdentity("me", "token", "https://server.example")

        val result = repository.send(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_PARTNER), result)
    }

    @Test
    fun `send encrypts the message so only the partner can read it and marks it SENT`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        val partner = PartnerEntity(
            displayName = "Robin",
            publicKey = recipientIdentity.publicKeyBase64,
            remoteDeviceId = "partner-device",
            serverBaseUrl = "https://server.example",
        )
        coEvery { partnerDao.get() } returns partner
        coEvery { deviceIdentityStore.getIdentity() } returns DeviceIdentity("me", "my-token", "https://server.example")
        coEvery { mailboxApi.submit("https://server.example", "my-token", "partner-device", any()) } returns
            MailboxResult.Success(MailboxSubmissionResponseDto("server-message-1"))
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { negotiationTurnDao.getForMessage(1L) } returns emptyList()

        val result = repository.send(1L)

        assertEquals(OutgoingActionResult.Success, result)
        coVerify {
            outgoingMessageDao.upsert(
                match { it.status == OutgoingMessageStatus.SENT && it.serverMessageId == "server-message-1" },
            )
        }

        val submissionSlot = io.mockk.slot<com.wafflehq.talktome.data.network.MailboxSubmissionDto>()
        coVerify { mailboxApi.submit(any(), any(), any(), capture(submissionSlot)) }
        val decrypted = recipientIdentity.decryptOwn(Base64.getDecoder().decode(submissionSlot.captured.ciphertext))
        val envelope = Json.decodeFromString(OutgoingMessageEnvelope.serializer(), String(decrypted, Charsets.UTF_8))
        assertEquals(1L, envelope.senderMessageRef)
        assertEquals("Ich wünsche mir mehr Zeit zu zweit", envelope.text)
    }

    @Test
    fun `send maps a submit failure without marking the message SENT`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        val partner = PartnerEntity(displayName = "Robin", publicKey = recipientIdentity.publicKeyBase64, remoteDeviceId = "partner-device", serverBaseUrl = "https://server.example")
        coEvery { partnerDao.get() } returns partner
        coEvery { deviceIdentityStore.getIdentity() } returns DeviceIdentity("me", "my-token", "https://server.example")
        coEvery { mailboxApi.submit(any(), any(), any(), any()) } returns MailboxResult.Failure(MailboxErrorReason.NOT_FOUND)

        val result = repository.send(1L)

        assertEquals(OutgoingActionResult.Failure(OutgoingActionErrorReason.SERVER_ERROR), result)
        coVerify(exactly = 0) { outgoingMessageDao.upsert(match { it.status == OutgoingMessageStatus.SENT }) }
    }

    @Test
    fun `handleRejection for an unknown message is processed without side effects`() = runTest {
        val envelope = RejectionEnvelope(senderMessageRef = 999L, feedback = "nope")
        coEvery { outgoingMessageDao.getById(999L) } returns null

        val outcome = repository.handleRejection(Json.encodeToString(RejectionEnvelope.serializer(), envelope).toByteArray())

        assertEquals(MailboxItemOutcome.PROCESSED, outcome)
        coVerify(exactly = 0) { outgoingMessageDao.upsert(any()) }
    }

    @Test
    fun `handleRejection retries later when there is no api key yet`() = runTest {
        val envelope = RejectionEnvelope(senderMessageRef = 1L, feedback = "nope")
        coEvery { outgoingMessageDao.getById(1L) } returns draft(status = OutgoingMessageStatus.SENT)
        coEvery { secureApiKeyStore.getApiKey() } returns null

        val outcome = repository.handleRejection(Json.encodeToString(RejectionEnvelope.serializer(), envelope).toByteArray())

        assertEquals(MailboxItemOutcome.RETRY_LATER, outcome)
        coVerify(exactly = 0) { outgoingMessageDao.upsert(any()) }
    }

    @Test
    fun `handleRejection reopens negotiation and records the coach reply`() = runTest {
        val envelope = RejectionEnvelope(senderMessageRef = 1L, feedback = "Klingt vorwurfsvoll")
        coEvery { outgoingMessageDao.getById(1L) } returns draft(status = OutgoingMessageStatus.SENT, rejectionCount = 1)
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } returns GeminiGenerateContentResult.Success("Versuch es sachlicher")
        coEvery { negotiationTurnDao.getForMessage(1L) } returns emptyList()

        val outcome = repository.handleRejection(Json.encodeToString(RejectionEnvelope.serializer(), envelope).toByteArray())

        assertEquals(MailboxItemOutcome.PROCESSED, outcome)
        coVerify {
            outgoingMessageDao.upsert(
                match {
                    it.status == OutgoingMessageStatus.NEGOTIATING &&
                        it.rejectionCount == 2 &&
                        it.lastRejectionReason == "Klingt vorwurfsvoll"
                },
            )
        }
        coVerify { negotiationTurnDao.insert(match { it.sender == NegotiationTurnSender.MEDIATOR && it.text == "Versuch es sachlicher" }) }
    }

    @Test
    fun `isEscalating is true once the rejection count reaches the threshold`() {
        assertFalse(repository.isEscalating(draft(rejectionCount = 2)))
        assertTrue(repository.isEscalating(draft(rejectionCount = 3)))
    }

    @Test
    fun `parseFriendOpinions returns an empty list for missing or invalid json`() {
        assertTrue(repository.parseFriendOpinions(draft()).isEmpty())
        assertTrue(repository.parseFriendOpinions(draft().copy(friendOpinionsJson = "not json")).isEmpty())
    }

    @Test
    fun `parseFriendOpinions round trips stored opinions`() = runTest {
        coEvery { outgoingMessageDao.getById(1L) } returns draft()
        coEvery { secureApiKeyStore.getApiKey() } returns "key"
        coEvery { geminiClient.generateContent(any(), any(), any(), any(), any()) } answers {
            val systemInstruction = secondArg<String>()
            if (systemInstruction.contains("neutraler Vermittler. Eine Person will")) {
                GeminiGenerateContentResult.Success("Zusammenfassung")
            } else {
                GeminiGenerateContentResult.Success("Meinung")
            }
        }
        val slot = io.mockk.slot<OutgoingMessageEntity>()
        coEvery { outgoingMessageDao.upsert(capture(slot)) } returns 1L

        repository.startNegotiation(1L)

        val stored = slot.captured
        val parsed = repository.parseFriendOpinions(stored)
        assertEquals(4, parsed.size)
        assertTrue(parsed.all { it.opinion == "Meinung" })
    }
}
