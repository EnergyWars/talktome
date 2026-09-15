package com.wafflehq.talktome.data.network

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

class MailboxApiTest {

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun clientReturning(status: HttpStatusCode, body: String = "{}"): HttpClient =
        HttpClient(MockEngine { respond(content = body, status = status, headers = jsonHeaders()) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun `registerDevice returns Success with the decoded body`() = runTest {
        val api = MailboxApi(clientReturning(HttpStatusCode.OK, """{"deviceId":"d1","token":"t1"}"""))

        val result = api.registerDevice("https://server.example")

        assertEquals(MailboxResult.Success(DeviceRegistrationDto("d1", "t1")), result)
    }

    @Test
    fun `submit returns Success with the message id`() = runTest {
        val api = MailboxApi(clientReturning(HttpStatusCode.Created, """{"messageId":"m1"}"""))

        val result = api.submit(
            "https://server.example",
            "token",
            "recipient",
            MailboxSubmissionDto("sender", "MESSAGE", "cipher"),
        )

        assertEquals(MailboxResult.Success(MailboxSubmissionResponseDto("m1")), result)
    }

    @Test
    fun `submit maps 404 to NOT_FOUND`() = runTest {
        val api = MailboxApi(clientReturning(HttpStatusCode.NotFound))

        val result = api.submit("https://server.example", "token", "recipient", MailboxSubmissionDto("sender", "MESSAGE", "cipher"))

        assertEquals(MailboxResult.Failure(MailboxErrorReason.NOT_FOUND), result)
    }

    @Test
    fun `submit maps 429 to RATE_LIMITED`() = runTest {
        val api = MailboxApi(clientReturning(HttpStatusCode.TooManyRequests))

        val result = api.submit("https://server.example", "token", "recipient", MailboxSubmissionDto("sender", "MESSAGE", "cipher"))

        assertEquals(MailboxResult.Failure(MailboxErrorReason.RATE_LIMITED), result)
    }

    @Test
    fun `submit maps 401 and 403 to UNAUTHORIZED`() = runTest {
        val unauthorized = MailboxApi(clientReturning(HttpStatusCode.Unauthorized))
            .submit("https://server.example", "token", "recipient", MailboxSubmissionDto("sender", "MESSAGE", "cipher"))
        val forbidden = MailboxApi(clientReturning(HttpStatusCode.Forbidden))
            .submit("https://server.example", "token", "recipient", MailboxSubmissionDto("sender", "MESSAGE", "cipher"))

        assertEquals(MailboxResult.Failure(MailboxErrorReason.UNAUTHORIZED), unauthorized)
        assertEquals(MailboxResult.Failure(MailboxErrorReason.UNAUTHORIZED), forbidden)
    }

    @Test
    fun `pull returns the list of pending items`() = runTest {
        val body = """[{"messageId":"m1","senderDeviceId":"s1","kind":"MESSAGE","ciphertext":"c1","createdAt":1}]"""
        val api = MailboxApi(clientReturning(HttpStatusCode.OK, body))

        val result = api.pull("https://server.example", "token")

        assertEquals(
            MailboxResult.Success(listOf(MailboxItemDto("m1", "s1", "MESSAGE", "c1", 1))),
            result,
        )
    }

    @Test
    fun `acknowledge returns Success on 204`() = runTest {
        val api = MailboxApi(clientReturning(HttpStatusCode.NoContent, ""))

        val result = api.acknowledge("https://server.example", "token", "m1")

        assertEquals(MailboxResult.Success(Unit), result)
    }

    @Test
    fun `acknowledge maps 404 to NOT_FOUND`() = runTest {
        val api = MailboxApi(clientReturning(HttpStatusCode.NotFound, ""))

        val result = api.acknowledge("https://server.example", "token", "unknown")

        assertEquals(MailboxResult.Failure(MailboxErrorReason.NOT_FOUND), result)
    }

    @Test
    fun `network failures are mapped to NETWORK`() = runTest {
        val failingClient = HttpClient(MockEngine { throw java.io.IOException("no network") }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = MailboxApi(failingClient)

        val result = api.registerDevice("https://server.example")

        assertEquals(MailboxResult.Failure(MailboxErrorReason.NETWORK), result)
    }

    @Test
    fun `trailing slash in server base url is normalized`() = runTest {
        var requestedUrl = ""
        val client = HttpClient(MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = MailboxApi(client)

        api.registerDevice("https://server.example/")

        assertTrue(requestedUrl.endsWith("/devices"))
        assertTrue(requestedUrl.contains("//devices").not())
    }
}
