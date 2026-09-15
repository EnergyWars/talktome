package com.wafflehq.talktome.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DeviceRegistrationDto(val deviceId: String, val token: String)

@Serializable
data class MailboxSubmissionDto(val senderDeviceId: String, val kind: String, val ciphertext: String)

@Serializable
data class MailboxSubmissionResponseDto(val messageId: String)

@Serializable
data class MailboxItemDto(
    val messageId: String,
    val senderDeviceId: String,
    val kind: String,
    val ciphertext: String,
    val createdAt: Long,
)

object MailboxKind {
    const val MESSAGE = "MESSAGE"
    const val REJECTION = "REJECTION"
    const val PAIRING_ACK = "PAIRING_ACK"
}

enum class MailboxErrorReason {
    NETWORK,
    UNAUTHORIZED,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER_ERROR,
}

sealed interface MailboxResult<out T> {
    data class Success<out T>(val value: T) : MailboxResult<T>
    data class Failure(val reason: MailboxErrorReason) : MailboxResult<Nothing>
}

@Singleton
class MailboxApi @Inject constructor(private val httpClient: HttpClient) {

    suspend fun registerDevice(serverBaseUrl: String): MailboxResult<DeviceRegistrationDto> =
        request { httpClient.post(baseUrl(serverBaseUrl, "/devices")) }

    suspend fun submit(
        serverBaseUrl: String,
        token: String,
        recipientDeviceId: String,
        submission: MailboxSubmissionDto,
    ): MailboxResult<MailboxSubmissionResponseDto> = request {
        httpClient.post(baseUrl(serverBaseUrl, "/mailbox/$recipientDeviceId")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(submission)
        }
    }

    suspend fun pull(serverBaseUrl: String, token: String): MailboxResult<List<MailboxItemDto>> = request {
        httpClient.get(baseUrl(serverBaseUrl, "/mailbox")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun acknowledge(serverBaseUrl: String, token: String, messageId: String): MailboxResult<Unit> = try {
        val response = httpClient.delete(baseUrl(serverBaseUrl, "/mailbox/$messageId")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        when (response.status) {
            HttpStatusCode.NoContent -> MailboxResult.Success(Unit)
            else -> MailboxResult.Failure(reasonFor(response.status))
        }
    } catch (e: Exception) {
        MailboxResult.Failure(MailboxErrorReason.NETWORK)
    }

    private fun baseUrl(serverBaseUrl: String, path: String): String = serverBaseUrl.trimEnd('/') + path

    private fun reasonFor(status: HttpStatusCode): MailboxErrorReason = when (status) {
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> MailboxErrorReason.UNAUTHORIZED
        HttpStatusCode.NotFound -> MailboxErrorReason.NOT_FOUND
        HttpStatusCode.TooManyRequests -> MailboxErrorReason.RATE_LIMITED
        else -> MailboxErrorReason.SERVER_ERROR
    }

    private suspend inline fun <reified T> request(call: () -> HttpResponse): MailboxResult<T> = try {
        val response = call()
        when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> MailboxResult.Success(response.body())
            else -> MailboxResult.Failure(reasonFor(response.status))
        }
    } catch (e: Exception) {
        MailboxResult.Failure(MailboxErrorReason.NETWORK)
    }
}
