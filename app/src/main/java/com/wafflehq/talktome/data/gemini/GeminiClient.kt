package com.wafflehq.talktome.data.gemini

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

enum class GeminiRole(val wireValue: String) {
    USER("user"),
    MODEL("model"),
}

data class GeminiTurn(val role: GeminiRole, val text: String)

@Singleton
class GeminiClient(
    private val httpClient: HttpClient,
    private val delayProvider: suspend (Long) -> Unit,
) {

    @Inject
    constructor(httpClient: HttpClient) : this(httpClient, { delay(it) })

    suspend fun verifyApiKey(apiKey: String): GeminiConnectionResult {
        if (apiKey.isBlank()) return GeminiConnectionResult.InvalidApiKey
        return try {
            val response = httpClient.get("$BASE_URL/models") {
                header(API_KEY_HEADER, apiKey)
            }
            statusToConnectionResult(response.status)
        } catch (e: Exception) {
            GeminiConnectionResult.NetworkError
        }
    }

    suspend fun generateContent(
        apiKey: String,
        systemInstruction: String,
        userContent: String,
        model: String = DEFAULT_MODEL,
        history: List<GeminiTurn> = emptyList(),
    ): GeminiGenerateContentResult {
        if (userContent.length > MAX_INPUT_CHARACTERS) {
            return GeminiGenerateContentResult.Error(GeminiErrorReason.INPUT_TOO_LONG)
        }

        var attempt = 0
        var lastError = GeminiErrorReason.UNKNOWN
        while (attempt <= MAX_RETRIES) {
            val result = performGenerateContent(apiKey, systemInstruction, userContent, model, history)
            if (result is GeminiGenerateContentResult.Success) return result

            val reason = (result as GeminiGenerateContentResult.Error).reason
            if (!isRetryable(reason) || attempt == MAX_RETRIES) {
                return result
            }
            lastError = reason
            delayProvider(backoffDelayMillis(attempt))
            attempt++
        }
        return GeminiGenerateContentResult.Error(lastError)
    }

    private suspend fun performGenerateContent(
        apiKey: String,
        systemInstruction: String,
        userContent: String,
        model: String,
        history: List<GeminiTurn>,
    ): GeminiGenerateContentResult {
        return try {
            val historyContents = history.map { GeminiContent(role = it.role.wireValue, parts = listOf(GeminiPart(it.text))) }
            val request = GeminiGenerateContentRequest(
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemInstruction))),
                contents = historyContents + GeminiContent(role = "user", parts = listOf(GeminiPart(userContent))),
            )
            val response = httpClient.post("$BASE_URL/models/$model:generateContent") {
                header(API_KEY_HEADER, apiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status != HttpStatusCode.OK) {
                return GeminiGenerateContentResult.Error(statusToErrorReason(response.status))
            }
            val body: GeminiGenerateContentResponse = response.body()
            val text = body.candidates.firstOrNull()?.content?.parts?.joinToString(separator = "") { it.text }
            if (text.isNullOrBlank()) {
                GeminiGenerateContentResult.Error(GeminiErrorReason.EMPTY_RESPONSE)
            } else {
                GeminiGenerateContentResult.Success(text)
            }
        } catch (e: Exception) {
            GeminiGenerateContentResult.Error(GeminiErrorReason.NETWORK)
        }
    }

    private fun isRetryable(reason: GeminiErrorReason): Boolean =
        reason == GeminiErrorReason.RATE_LIMITED || reason == GeminiErrorReason.NETWORK

    private fun backoffDelayMillis(attempt: Int): Long =
        INITIAL_BACKOFF_MILLIS * (1L shl attempt)

    private fun statusToConnectionResult(status: HttpStatusCode): GeminiConnectionResult = when (status) {
        HttpStatusCode.OK -> GeminiConnectionResult.Success
        HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            GeminiConnectionResult.InvalidApiKey
        HttpStatusCode.TooManyRequests -> GeminiConnectionResult.RateLimited
        else -> GeminiConnectionResult.UnknownError(status.value)
    }

    private fun statusToErrorReason(status: HttpStatusCode): GeminiErrorReason = when (status) {
        HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            GeminiErrorReason.INVALID_API_KEY
        HttpStatusCode.TooManyRequests -> GeminiErrorReason.RATE_LIMITED
        else -> GeminiErrorReason.UNKNOWN
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val API_KEY_HEADER = "x-goog-api-key"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        const val MAX_INPUT_CHARACTERS = 100_000
        const val MAX_RETRIES = 2
        const val INITIAL_BACKOFF_MILLIS = 500L
    }
}
