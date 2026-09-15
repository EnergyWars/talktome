package com.wafflehq.talktome.data.gemini

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientTest {

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun clientReturning(
        status: HttpStatusCode,
        body: String = "{}",
    ): HttpClient = HttpClient(MockEngine { _ ->
        respond(
            content = body,
            status = status,
            headers = jsonHeaders(),
        )
    }) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun noDelay(): suspend (Long) -> Unit = {}

    @Test
    fun `verifyApiKey returns Success for HTTP 200`() = runTest {
        val client = GeminiClient(clientReturning(HttpStatusCode.OK), noDelay())

        val result = client.verifyApiKey("valid-key")

        assertEquals(GeminiConnectionResult.Success, result)
    }

    @Test
    fun `verifyApiKey returns InvalidApiKey for HTTP 400`() = runTest {
        val client = GeminiClient(clientReturning(HttpStatusCode.BadRequest), noDelay())

        val result = client.verifyApiKey("bad-key")

        assertEquals(GeminiConnectionResult.InvalidApiKey, result)
    }

    @Test
    fun `verifyApiKey returns InvalidApiKey for HTTP 403`() = runTest {
        val client = GeminiClient(clientReturning(HttpStatusCode.Forbidden), noDelay())

        val result = client.verifyApiKey("bad-key")

        assertEquals(GeminiConnectionResult.InvalidApiKey, result)
    }

    @Test
    fun `verifyApiKey returns RateLimited for HTTP 429`() = runTest {
        val client = GeminiClient(clientReturning(HttpStatusCode.TooManyRequests), noDelay())

        val result = client.verifyApiKey("some-key")

        assertEquals(GeminiConnectionResult.RateLimited, result)
    }

    @Test
    fun `verifyApiKey returns InvalidApiKey for blank key without a network call`() = runTest {
        val client = GeminiClient(clientReturning(HttpStatusCode.OK), noDelay())

        val result = client.verifyApiKey("   ")

        assertEquals(GeminiConnectionResult.InvalidApiKey, result)
    }

    @Test
    fun `verifyApiKey returns NetworkError when the engine throws`() = runTest {
        val failingClient = HttpClient(MockEngine { throw java.io.IOException("no network") }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = GeminiClient(failingClient, noDelay())

        val result = client.verifyApiKey("some-key")

        assertEquals(GeminiConnectionResult.NetworkError, result)
    }

    @Test
    fun `generateContent returns Success with extracted text`() = runTest {
        val body = """{"candidates":[{"content":{"parts":[{"text":"Antwort vom Modell"}]}}]}"""
        val client = GeminiClient(clientReturning(HttpStatusCode.OK, body), noDelay())

        val result = client.generateContent("key", "system", "user text")

        assertEquals(GeminiGenerateContentResult.Success("Antwort vom Modell"), result)
    }

    @Test
    fun `generateContent joins multiple parts of the first candidate`() = runTest {
        val body = """{"candidates":[{"content":{"parts":[{"text":"Teil 1 "},{"text":"Teil 2"}]}}]}"""
        val client = GeminiClient(clientReturning(HttpStatusCode.OK, body), noDelay())

        val result = client.generateContent("key", "system", "user text")

        assertEquals(GeminiGenerateContentResult.Success("Teil 1 Teil 2"), result)
    }

    @Test
    fun `generateContent returns EMPTY_RESPONSE when there are no candidates`() = runTest {
        val client = GeminiClient(clientReturning(HttpStatusCode.OK, """{"candidates":[]}"""), noDelay())

        val result = client.generateContent("key", "system", "user text")

        assertEquals(GeminiGenerateContentResult.Error(GeminiErrorReason.EMPTY_RESPONSE), result)
    }

    @Test
    fun `generateContent rejects input longer than the character limit without a network call`() = runTest {
        var callCount = 0
        val countingClient = HttpClient(MockEngine { _ ->
            callCount++
            respond("{}", HttpStatusCode.OK)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = GeminiClient(countingClient, noDelay())
        val tooLong = "a".repeat(100_001)

        val result = client.generateContent("key", "system", tooLong)

        assertEquals(GeminiGenerateContentResult.Error(GeminiErrorReason.INPUT_TOO_LONG), result)
        assertEquals(0, callCount)
    }

    @Test
    fun `generateContent retries on rate limiting and eventually succeeds`() = runTest {
        var attempt = 0
        val flakyClient = HttpClient(MockEngine { _ ->
            attempt++
            if (attempt < 3) {
                respond("{}", HttpStatusCode.TooManyRequests)
            } else {
                respond(
                    content = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = GeminiClient(flakyClient, noDelay())

        val result = client.generateContent("key", "system", "user text")

        assertEquals(GeminiGenerateContentResult.Success("ok"), result)
        assertEquals(3, attempt)
    }

    @Test
    fun `generateContent gives up after exhausting retries on persistent rate limiting`() = runTest {
        var attempt = 0
        val alwaysLimited = HttpClient(MockEngine { _ ->
            attempt++
            respond("{}", HttpStatusCode.TooManyRequests)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = GeminiClient(alwaysLimited, noDelay())

        val result = client.generateContent("key", "system", "user text")

        assertEquals(GeminiGenerateContentResult.Error(GeminiErrorReason.RATE_LIMITED), result)
        assertTrue(attempt in 1..10)
    }

    @Test
    fun `generateContent sends prior history turns before the new user turn`() = runTest {
        var capturedBody = ""
        val client = HttpClient(MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(
                content = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val geminiClient = GeminiClient(client, noDelay())
        val history = listOf(
            GeminiTurn(GeminiRole.USER, "Erste Nachricht"),
            GeminiTurn(GeminiRole.MODEL, "Erste Antwort"),
        )

        geminiClient.generateContent("key", "system", "Zweite Nachricht", history = history)

        assertTrue(capturedBody.contains("Erste Nachricht"))
        assertTrue(capturedBody.contains("Erste Antwort"))
        assertTrue(capturedBody.contains("Zweite Nachricht"))
        assertTrue(capturedBody.indexOf("Erste Nachricht") < capturedBody.indexOf("Zweite Nachricht"))
    }

    @Test
    fun `generateContent does not retry on invalid api key`() = runTest {
        var attempt = 0
        val invalidKeyClient = HttpClient(MockEngine { _ ->
            attempt++
            respond("{}", HttpStatusCode.Unauthorized)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = GeminiClient(invalidKeyClient, noDelay())

        val result = client.generateContent("key", "system", "user text")

        assertEquals(GeminiGenerateContentResult.Error(GeminiErrorReason.INVALID_API_KEY), result)
        assertEquals(1, attempt)
    }
}
