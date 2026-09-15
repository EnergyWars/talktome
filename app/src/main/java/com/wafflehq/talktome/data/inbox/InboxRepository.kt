package com.wafflehq.talktome.data.inbox

import com.wafflehq.talktome.data.crypto.E2eIdentity
import com.wafflehq.talktome.data.db.InboxMessageDao
import com.wafflehq.talktome.data.db.InboxMessageEntity
import com.wafflehq.talktome.data.db.InboxMessageStatus
import com.wafflehq.talktome.data.db.MediatorAgentRole
import com.wafflehq.talktome.data.db.MediatorNoteDao
import com.wafflehq.talktome.data.db.MediatorNoteEntity
import com.wafflehq.talktome.data.db.PartnerDao
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiGenerateContentResult
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxItemDto
import com.wafflehq.talktome.data.network.MailboxItemOutcome
import com.wafflehq.talktome.data.network.MailboxKind
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.network.MailboxSubmissionDto
import com.wafflehq.talktome.data.network.OutgoingMessageEnvelope
import com.wafflehq.talktome.data.network.RejectionEnvelope
import com.wafflehq.talktome.data.profile.ProfileRepository
import com.wafflehq.talktome.data.prompts.GatekeeperVerdict
import com.wafflehq.talktome.data.prompts.MediatorPrompts
import com.wafflehq.talktome.data.prompts.NotesContext
import com.wafflehq.talktome.data.prompts.PromptBuilder
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import com.wafflehq.talktome.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboxRepository @Inject constructor(
    private val inboxMessageDao: InboxMessageDao,
    private val mediatorNoteDao: MediatorNoteDao,
    private val profileRepository: ProfileRepository,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val geminiClient: GeminiClient,
    private val partnerDao: PartnerDao,
    private val mailboxApi: MailboxApi,
    private val e2eIdentity: E2eIdentity,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val approvedMessages: Flow<List<InboxMessageEntity>> = inboxMessageDao.observeByStatus(InboxMessageStatus.APPROVED)

    suspend fun handleIncoming(
        plaintext: ByteArray,
        item: MailboxItemDto,
        serverBaseUrl: String,
        token: String,
        ownDeviceId: String,
    ): MailboxItemOutcome {
        val envelope = try {
            json.decodeFromString(OutgoingMessageEnvelope.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            return MailboxItemOutcome.PROCESSED
        }
        val apiKey = secureApiKeyStore.getApiKey() ?: return MailboxItemOutcome.RETRY_LATER
        val partner = partnerDao.get()
        val partnerPublicKey = partner?.publicKey
        val partnerDeviceId = partner?.remoteDeviceId
        if (partnerPublicKey == null || partnerDeviceId == null) return MailboxItemOutcome.RETRY_LATER

        val filterText = profileRepository.profile.first().filterText
        val notes = mediatorNoteDao.observeByRole(MediatorAgentRole.RECEIVER_GATEKEEPER).first()
        val systemInstruction = NotesContext.append(MediatorPrompts.receiverGatekeeper(filterText), notes)
        val model = settingsRepository.geminiModel.first().wireId
        val result = geminiClient.generateContent(apiKey, systemInstruction, PromptBuilder.wrapUserMessage(envelope.text), model = model)
        val rawText = (result as? GeminiGenerateContentResult.Success)?.text ?: return MailboxItemOutcome.RETRY_LATER
        val verdict = GatekeeperVerdict.parse(rawText)

        if (verdict.approved) {
            inboxMessageDao.upsert(
                InboxMessageEntity(
                    content = envelope.text,
                    status = InboxMessageStatus.APPROVED,
                    mediatorFeedback = verdict.feedback,
                    receivedAt = System.currentTimeMillis(),
                    serverMessageId = item.messageId,
                    senderDeviceId = item.senderDeviceId,
                ),
            )
            writeNote(MediatorAgentRole.RECEIVER_GATEKEEPER, "Zugelassene Nachricht. Feedback an den Empfänger: ${verdict.feedback}")
            return MailboxItemOutcome.PROCESSED
        }

        val rejection = RejectionEnvelope(senderMessageRef = envelope.senderMessageRef, feedback = verdict.feedback)
        val ciphertext = e2eIdentity.encryptFor(
            partnerPublicKey,
            json.encodeToString(RejectionEnvelope.serializer(), rejection).toByteArray(Charsets.UTF_8),
        )
        val submission = MailboxSubmissionDto(
            senderDeviceId = ownDeviceId,
            kind = MailboxKind.REJECTION,
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )
        return when (mailboxApi.submit(serverBaseUrl, token, partnerDeviceId, submission)) {
            is MailboxResult.Success -> {
                writeNote(MediatorAgentRole.RECEIVER_GATEKEEPER, "Nachricht abgelehnt. Interner Grund: ${verdict.feedback}")
                MailboxItemOutcome.PROCESSED
            }
            is MailboxResult.Failure -> MailboxItemOutcome.RETRY_LATER
        }
    }

    private suspend fun writeNote(role: MediatorAgentRole, transcript: String) {
        val apiKey = secureApiKeyStore.getApiKey() ?: return
        val model = settingsRepository.geminiModel.first().wireId
        val result = geminiClient.generateContent(apiKey, MediatorPrompts.noteTaker(role), PromptBuilder.wrapUserMessage(transcript), model = model)
        if (result is GeminiGenerateContentResult.Success) {
            mediatorNoteDao.insert(MediatorNoteEntity(role = role, noteText = result.text, createdAt = System.currentTimeMillis()))
        }
    }
}
