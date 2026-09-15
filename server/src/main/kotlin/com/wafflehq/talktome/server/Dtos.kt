package com.wafflehq.talktome.server

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationResponse(val deviceId: String, val token: String)

@Serializable
data class MailboxSubmission(val senderDeviceId: String, val kind: String, val ciphertext: String)

@Serializable
data class MailboxSubmissionResponse(val messageId: String)

@Serializable
data class MailboxItem(
    val messageId: String,
    val senderDeviceId: String,
    val kind: String,
    val ciphertext: String,
    val createdAt: Long,
)

@Serializable
data class ErrorResponse(val error: String)
