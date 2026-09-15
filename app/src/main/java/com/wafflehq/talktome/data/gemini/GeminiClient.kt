package com.wafflehq.talktome.data.gemini

import android.util.Log
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
        if (apiKey.isBlank()) {
            Log.w(TAG, "verifyApiKey: rejected, key input is blank")
            return GeminiConnectionResult.InvalidApiKey
        }
        Log.d(TAG, "verifyApiKey: requesting GET $BASE_URL/models (keyLength=${apiKey.length})")
        return try {
            val response = httpClient.get("$BASE_URL/models") {
                header(API_KEY_HEADER, apiKey)
            }
            Log.d(TAG, "verifyApiKey: received HTTP ${response.status.value}")
            statusToConnectionResult(response.status)
        } catch (e: Exception) {
            Log.e(TAG, "verifyApiKey: request failed with ${e.javaClass.simpleName}: ${e.message}", e)
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
            Log.w(TAG, "generateContent: rejected, input length ${userContent.length} exceeds $MAX_INPUT_CHARACTERS")
            return GeminiGenerateContentResult.Error(GeminiErrorReason.INPUT_TOO_LONG)
        }

        var attempt = 0
        var lastError = GeminiErrorReason.UNKNOWN
        while (attempt <= MAX_RETRIES) {
            Log.d(TAG, "generateContent: attempt ${attempt + 1}/${MAX_RETRIES + 1} for model=$model, historyTurns=${history.size}")
            val result = performGenerateContent(apiKey, systemInstruction, userContent, model, history)
            if (result is GeminiGenerateContentResult.Success) {
                Log.d(TAG, "generateContent: succeeded on attempt ${attempt + 1}")
                return result
            }

            val reason = (result as GeminiGenerateContentResult.Error).reason
            if (!isRetryable(reason) || attempt == MAX_RETRIES) {
                Log.w(TAG, "generateContent: giving up after attempt ${attempt + 1}, reason=$reason, retryable=${isRetryable(reason)}")
                return result
            }
            lastError = reason
            val backoff = backoffDelayMillis(attempt)
            Log.w(TAG, "generateContent: attempt ${attempt + 1} failed with $reason, retrying in ${backoff}ms")
            delayProvider(backoff)
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
            Log.d(TAG, "performGenerateContent: received HTTP ${response.status.value} for model=$model")
            if (response.status != HttpStatusCode.OK) {
                val errorBody = runCatching { response.body<String>() }.getOrNull()
                Log.w(TAG, "performGenerateContent: non-OK response ${response.status.value}, body=$errorBody")
                return GeminiGenerateContentResult.Error(statusToErrorReason(response.status))
            }
            val body: GeminiGenerateContentResponse = response.body()
            val candidate = body.candidates.firstOrNull()
            val text = candidate?.content?.parts?.joinToString(separator = "") { it.text }
            val blockReason = body.promptFeedback?.blockReason
            val finishReason = candidate?.finishReason
            if (text.isNullOrBlank()) {
                Log.w(
                    TAG,
                    "performGenerateContent: empty response, candidateCount=${body.candidates.size}, " +
                        "finishReason=$finishReason, promptBlockReason=$blockReason",
                )
                val reason = if (blockReason != null || finishReason == "SAFETY" || finishReason == "RECITATION") {
                    GeminiErrorReason.BLOCKED_BY_SAFETY_FILTER
                } else {
                    GeminiErrorReason.EMPTY_RESPONSE
                }
                GeminiGenerateContentResult.Error(reason)
            } else {
                GeminiGenerateContentResult.Success(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "performGenerateContent: request failed with ${e.javaClass.simpleName}: ${e.message}", e)
            GeminiGenerateContentResult.Error(GeminiErrorReason.NETWORK)
        }
    }

    private fun isRetryable(reason: GeminiErrorReason): Boolean =
        reason == GeminiErrorReason.RATE_LIMITED ||
            reason == GeminiErrorReason.NETWORK ||
            reason == GeminiErrorReason.SERVICE_UNAVAILABLE

    private fun backoffDelayMillis(attempt: Int): Long =
        INITIAL_BACKOFF_MILLIS * (1L shl attempt)

    private fun statusToConnectionResult(status: HttpStatusCode): GeminiConnectionResult = when (status) {
        HttpStatusCode.OK -> GeminiConnectionResult.Success
        HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            GeminiConnectionResult.InvalidApiKey
        HttpStatusCode.TooManyRequests -> GeminiConnectionResult.RateLimited
        HttpStatusCode.ServiceUnavailable -> GeminiConnectionResult.ServiceUnavailable
        else -> GeminiConnectionResult.UnknownError(status.value)
    }

    private fun statusToErrorReason(status: HttpStatusCode): GeminiErrorReason = when (status) {
        HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
            GeminiErrorReason.INVALID_API_KEY
        HttpStatusCode.TooManyRequests -> GeminiErrorReason.RATE_LIMITED
        HttpStatusCode.ServiceUnavailable -> GeminiErrorReason.SERVICE_UNAVAILABLE
        else -> GeminiErrorReason.UNKNOWN
    }

    private companion object {
        const val TAG = "GeminiClient"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val API_KEY_HEADER = "x-goog-api-key"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        const val MAX_INPUT_CHARACTERS = 100_000
        const val MAX_RETRIES = 2
        const val INITIAL_BACKOFF_MILLIS = 500L
    }
}
