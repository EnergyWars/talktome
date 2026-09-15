package com.wafflehq.talktome.data.network

import kotlinx.serialization.Serializable

@Serializable
data class OutgoingMessageEnvelope(val senderMessageRef: Long, val text: String)

@Serializable
data class RejectionEnvelope(val senderMessageRef: Long, val feedback: String)

enum class MailboxItemOutcome {
    PROCESSED,
    RETRY_LATER,
}
