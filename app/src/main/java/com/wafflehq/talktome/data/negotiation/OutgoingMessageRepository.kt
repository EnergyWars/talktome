package com.wafflehq.talktome.data.negotiation

import android.util.Log
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
import com.wafflehq.talktome.data.gemini.GeminiClient
import com.wafflehq.talktome.data.gemini.GeminiErrorReason
import com.wafflehq.talktome.data.gemini.GeminiGenerateContentResult
import com.wafflehq.talktome.data.gemini.GeminiRole
import com.wafflehq.talktome.data.gemini.GeminiTurn
import com.wafflehq.talktome.data.network.MailboxApi
import com.wafflehq.talktome.data.network.MailboxErrorReason
import com.wafflehq.talktome.data.network.MailboxItemOutcome
import com.wafflehq.talktome.data.network.MailboxKind
import com.wafflehq.talktome.data.network.MailboxResult
import com.wafflehq.talktome.data.network.MailboxSubmissionDto
import com.wafflehq.talktome.data.network.OutgoingMessageEnvelope
import com.wafflehq.talktome.data.network.RejectionEnvelope
import com.wafflehq.talktome.data.profile.ProfileRepository
import com.wafflehq.talktome.data.prompts.FriendOpinion
import com.wafflehq.talktome.data.prompts.MediatorContext
import com.wafflehq.talktome.data.prompts.MediatorPrompts
import com.wafflehq.talktome.data.prompts.NotesContext
import com.wafflehq.talktome.data.prompts.PromptBuilder
import com.wafflehq.talktome.data.security.SecureApiKeyStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

enum class OutgoingActionErrorReason {
    NO_API_KEY,
    INVALID_API_KEY,
    NO_PARTNER,
    NETWORK,
    BLOCKED_BY_SAFETY_FILTER,
    SERVICE_UNAVAILABLE,
    SERVER_ERROR,
}

sealed interface OutgoingActionResult {
    data object Success : OutgoingActionResult
    data class Failure(val reason: OutgoingActionErrorReason) : OutgoingActionResult
}

@Singleton
class OutgoingMessageRepository @Inject constructor(
    private val outgoingMessageDao: OutgoingMessageDao,
    private val negotiationTurnDao: NegotiationTurnDao,
    private val mediatorNoteDao: MediatorNoteDao,
    private val profileRepository: ProfileRepository,
    private val secureApiKeyStore: SecureApiKeyStore,
    private val geminiClient: GeminiClient,
    private val partnerDao: PartnerDao,
    private val mailboxApi: MailboxApi,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val e2eIdentity: E2eIdentity,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val opinionsSerializer = ListSerializer(FriendOpinion.serializer())

    val activeMessage: Flow<OutgoingMessageEntity?> = outgoingMessageDao.observeAll()
        .map { messages -> messages.firstOrNull { it.status != OutgoingMessageStatus.SENT } }

    fun negotiationTurns(messageId: Long): Flow<List<NegotiationTurnEntity>> =
        negotiationTurnDao.observeForMessage(messageId)

    fun parseFriendOpinions(message: OutgoingMessageEntity): List<FriendOpinion> {
        val raw = message.friendOpinionsJson ?: return emptyList()
        return try {
            json.decodeFromString(opinionsSerializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isEscalating(message: OutgoingMessageEntity): Boolean = message.rejectionCount >= ESCALATION_THRESHOLD

    suspend fun startDraft(text: String): Long {
        val now = System.currentTimeMillis()
        return outgoingMessageDao.upsert(
            OutgoingMessageEntity(draftText = text, status = OutgoingMessageStatus.DRAFT, createdAt = now, updatedAt = now),
        )
    }

    suspend fun startNegotiation(messageId: Long): OutgoingActionResult {
        Log.d(TAG, "startNegotiation: messageId=$messageId")
        val message = outgoingMessageDao.getById(messageId)
            ?: run {
                Log.w(TAG, "startNegotiation: no message found for id=$messageId")
                return OutgoingActionResult.Failure(OutgoingActionErrorReason.SERVER_ERROR)
            }
        if (message.status != OutgoingMessageStatus.DRAFT) return OutgoingActionResult.Success

        val apiKey = secureApiKeyStore.getApiKey()
            ?: run {
                Log.w(TAG, "startNegotiation: aborted, no API key stored")
                return OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_API_KEY)
            }
        val profile = profileRepository.profile.first()
        val context = MediatorContext(selfDescription = profile.selfDescription, partnerDescription = profile.partnerDescription)

        val opinionResults = coroutineScope {
            listOf(
                LABEL_PARTNER_FRIENDS_WITHOUT_CONTEXT to MediatorPrompts.partnerAdvisor(MediatorContext()),
                LABEL_PARTNER_FRIENDS_WITH_CONTEXT to MediatorPrompts.partnerAdvisor(context),
                LABEL_SENDER_FRIENDS_WITHOUT_CONTEXT to MediatorPrompts.selfAdvisor(MediatorContext()),
                LABEL_SENDER_FRIENDS_WITH_CONTEXT to MediatorPrompts.selfAdvisor(context),
            ).map { (label, systemInstruction) ->
                async { label to geminiClient.generateContent(apiKey, systemInstruction, PromptBuilder.wrapUserMessage(message.draftText)) }
            }.awaitAll()
        }
        val opinions = opinionResults.mapNotNull { (label, result) ->
            (result as? GeminiGenerateContentResult.Success)?.let { FriendOpinion(label, it.text) }
        }
        Log.d(TAG, "startNegotiation: opinions succeeded=${opinions.size}/${opinionResults.size}")
        if (opinions.isEmpty()) {
            val reasons = opinionResults.map { it.second }.filterIsInstance<GeminiGenerateContentResult.Error>().map { it.reason }
            Log.w(TAG, "startNegotiation: all opinion calls failed, reasons=$reasons")
            return OutgoingActionResult.Failure(reasons.toOutgoingReason())
        }

        val notes = mediatorNoteDao.observeByRole(MediatorAgentRole.NEUTRAL_MEDIATOR).first()
        val systemInstruction = NotesContext.append(MediatorPrompts.neutralMediator(), notes)
        val userContent = PromptBuilder.neutralMediatorUserContent(message.draftText, opinions)
        val summaryResult = geminiClient.generateContent(apiKey, systemInstruction, userContent)
        val summaryText = (summaryResult as? GeminiGenerateContentResult.Success)?.text
            ?: run {
                val reason = (summaryResult as GeminiGenerateContentResult.Error).reason
                Log.w(TAG, "startNegotiation: mediator summary call failed, reason=$reason")
                return OutgoingActionResult.Failure(reason.toOutgoingReason())
            }

        val now = System.currentTimeMillis()
        outgoingMessageDao.upsert(
            message.copy(
                status = OutgoingMessageStatus.NEGOTIATING,
                friendOpinionsJson = json.encodeToString(opinionsSerializer, opinions),
                updatedAt = now,
            ),
        )
        negotiationTurnDao.insert(
            NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.USER, text = message.draftText, createdAt = now),
        )
        negotiationTurnDao.insert(
            NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.MEDIATOR, text = summaryText, createdAt = now + 1),
        )
        return OutgoingActionResult.Success
    }

    suspend fun sendReply(messageId: Long, userText: String): OutgoingActionResult {
        Log.d(TAG, "sendReply: messageId=$messageId")
        val message = outgoingMessageDao.getById(messageId)
            ?: run {
                Log.w(TAG, "sendReply: no message found for id=$messageId")
                return OutgoingActionResult.Failure(OutgoingActionErrorReason.SERVER_ERROR)
            }
        val apiKey = secureApiKeyStore.getApiKey()
            ?: run {
                Log.w(TAG, "sendReply: aborted, no API key stored")
                return OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_API_KEY)
            }

        val history = negotiationTurnDao.getForMessage(messageId).map {
            GeminiTurn(if (it.sender == NegotiationTurnSender.USER) GeminiRole.USER else GeminiRole.MODEL, it.text)
        }
        val notes = mediatorNoteDao.observeByRole(MediatorAgentRole.NEUTRAL_MEDIATOR).first()
        val systemInstruction = NotesContext.append(MediatorPrompts.neutralMediator(), notes)
        val result = geminiClient.generateContent(apiKey, systemInstruction, PromptBuilder.wrapUserMessage(userText), history = history)
        val replyText = (result as? GeminiGenerateContentResult.Success)?.text
            ?: run {
                val reason = (result as GeminiGenerateContentResult.Error).reason
                Log.w(TAG, "sendReply: mediator reply call failed, reason=$reason")
                return OutgoingActionResult.Failure(reason.toOutgoingReason())
            }

        val now = System.currentTimeMillis()
        outgoingMessageDao.upsert(message.copy(draftText = userText, updatedAt = now))
        negotiationTurnDao.insert(NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.USER, text = userText, createdAt = now))
        negotiationTurnDao.insert(
            NegotiationTurnEntity(outgoingMessageId = messageId, sender = NegotiationTurnSender.MEDIATOR, text = replyText, createdAt = now + 1),
        )
        return OutgoingActionResult.Success
    }

    suspend fun send(messageId: Long): OutgoingActionResult {
        Log.d(TAG, "send: messageId=$messageId")
        val message = outgoingMessageDao.getById(messageId)
            ?: run {
                Log.w(TAG, "send: no message found for id=$messageId")
                return OutgoingActionResult.Failure(OutgoingActionErrorReason.SERVER_ERROR)
            }
        val partner = partnerDao.get()
        val remoteDeviceId = partner?.remoteDeviceId
        val remotePublicKey = partner?.publicKey
        val serverBaseUrl = partner?.serverBaseUrl
        val identity = deviceIdentityStore.getIdentity()
        if (remoteDeviceId == null || remotePublicKey == null || serverBaseUrl == null || identity == null) {
            Log.w(
                TAG,
                "send: aborted, missing pairing data (partner=${partner != null}, " +
                    "remoteDeviceId=${remoteDeviceId != null}, publicKey=${remotePublicKey != null}, " +
                    "serverBaseUrl=${serverBaseUrl != null}, deviceIdentity=${identity != null})",
            )
            return OutgoingActionResult.Failure(OutgoingActionErrorReason.NO_PARTNER)
        }

        val envelope = OutgoingMessageEnvelope(senderMessageRef = messageId, text = message.draftText)
        val ciphertext = e2eIdentity.encryptFor(
            remotePublicKey,
            json.encodeToString(OutgoingMessageEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8),
        )
        val submission = MailboxSubmissionDto(
            senderDeviceId = identity.deviceId,
            kind = MailboxKind.MESSAGE,
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )

        return when (val result = mailboxApi.submit(serverBaseUrl, identity.token, remoteDeviceId, submission)) {
            is MailboxResult.Success -> {
                Log.d(TAG, "send: mailbox submission succeeded, serverMessageId=${result.value.messageId}")
                outgoingMessageDao.upsert(
                    message.copy(
                        status = OutgoingMessageStatus.SENT,
                        serverMessageId = result.value.messageId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                writeNote(MediatorAgentRole.NEUTRAL_MEDIATOR, messageId)
                OutgoingActionResult.Success
            }
            is MailboxResult.Failure -> {
                Log.w(TAG, "send: mailbox submission failed, reason=${result.reason}")
                OutgoingActionResult.Failure(result.reason.toOutgoingReason())
            }
        }
    }

    suspend fun handleRejection(plaintext: ByteArray): MailboxItemOutcome {
        val envelope = try {
            json.decodeFromString(RejectionEnvelope.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            return MailboxItemOutcome.PROCESSED
        }
        val message = outgoingMessageDao.getById(envelope.senderMessageRef) ?: return MailboxItemOutcome.PROCESSED
        val apiKey = secureApiKeyStore.getApiKey() ?: return MailboxItemOutcome.RETRY_LATER

        val notes = mediatorNoteDao.observeByRole(MediatorAgentRole.SENDER_COACH).first()
        val systemInstruction = NotesContext.append(MediatorPrompts.senderCoach(), notes)
        val result = geminiClient.generateContent(apiKey, systemInstruction, PromptBuilder.wrapUserMessage(envelope.feedback))
        val coachText = (result as? GeminiGenerateContentResult.Success)?.text ?: FALLBACK_COACH_MESSAGE

        val now = System.currentTimeMillis()
        outgoingMessageDao.upsert(
            message.copy(
                status = OutgoingMessageStatus.NEGOTIATING,
                rejectionCount = message.rejectionCount + 1,
                lastRejectionReason = envelope.feedback,
                updatedAt = now,
            ),
        )
        negotiationTurnDao.insert(
            NegotiationTurnEntity(outgoingMessageId = message.id, sender = NegotiationTurnSender.MEDIATOR, text = coachText, createdAt = now),
        )
        writeNote(MediatorAgentRole.SENDER_COACH, message.id)
        return MailboxItemOutcome.PROCESSED
    }

    private suspend fun writeNote(role: MediatorAgentRole, messageId: Long) {
        val apiKey = secureApiKeyStore.getApiKey() ?: return
        val transcript = negotiationTurnDao.getForMessage(messageId).joinToString("\n") { "${it.sender}: ${it.text}" }
        if (transcript.isBlank()) return
        val result = geminiClient.generateContent(apiKey, MediatorPrompts.noteTaker(role), PromptBuilder.wrapUserMessage(transcript))
        if (result is GeminiGenerateContentResult.Success) {
            mediatorNoteDao.insert(MediatorNoteEntity(role = role, noteText = result.text, createdAt = System.currentTimeMillis()))
        }
    }

    private fun MailboxErrorReason.toOutgoingReason(): OutgoingActionErrorReason = when (this) {
        MailboxErrorReason.NETWORK -> OutgoingActionErrorReason.NETWORK
        else -> OutgoingActionErrorReason.SERVER_ERROR
    }

    private fun GeminiErrorReason.toOutgoingReason(): OutgoingActionErrorReason = when (this) {
        GeminiErrorReason.INVALID_API_KEY -> OutgoingActionErrorReason.INVALID_API_KEY
        GeminiErrorReason.NETWORK -> OutgoingActionErrorReason.NETWORK
        GeminiErrorReason.BLOCKED_BY_SAFETY_FILTER -> OutgoingActionErrorReason.BLOCKED_BY_SAFETY_FILTER
        GeminiErrorReason.SERVICE_UNAVAILABLE -> OutgoingActionErrorReason.SERVICE_UNAVAILABLE
        GeminiErrorReason.RATE_LIMITED, GeminiErrorReason.INPUT_TOO_LONG, GeminiErrorReason.EMPTY_RESPONSE, GeminiErrorReason.UNKNOWN ->
            OutgoingActionErrorReason.SERVER_ERROR
    }

    private fun List<GeminiErrorReason>.toOutgoingReason(): OutgoingActionErrorReason = when {
        isEmpty() -> OutgoingActionErrorReason.NETWORK
        any { it == GeminiErrorReason.INVALID_API_KEY } -> OutgoingActionErrorReason.INVALID_API_KEY
        any { it == GeminiErrorReason.SERVICE_UNAVAILABLE } -> OutgoingActionErrorReason.SERVICE_UNAVAILABLE
        else -> first().toOutgoingReason()
    }

    private companion object {
        const val TAG = "OutgoingMessageRepo"
        const val ESCALATION_THRESHOLD = 3
        const val LABEL_PARTNER_FRIENDS_WITHOUT_CONTEXT =
            "Freunde des Empfängers, ohne den Sender zu kennen oder den Empfänger zu berücksichtigen"
        const val LABEL_PARTNER_FRIENDS_WITH_CONTEXT =
            "Freunde des Empfängers, die sowohl Sender als auch Empfänger kennen"
        const val LABEL_SENDER_FRIENDS_WITHOUT_CONTEXT =
            "Freunde des Senders, ohne den Empfänger zu kennen oder den Sender zu berücksichtigen"
        const val LABEL_SENDER_FRIENDS_WITH_CONTEXT =
            "Freunde des Senders, die sowohl Sender als auch Empfänger kennen"
        const val FALLBACK_COACH_MESSAGE =
            "Dein Partner hat die Nachricht abgelehnt. Versuche es mit einer ruhigeren, konkreteren Formulierung erneut."
    }
}
