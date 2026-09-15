package com.wafflehq.talktome.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutingTest {

    private lateinit var database: MailboxDatabase

    @Before
    fun setUp() {
        database = MailboxDatabase("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `registering a device returns an id and token`() = testApplication {
        application { module(database) }
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val response = client.post("/devices")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<DeviceRegistrationResponse>()
        assertTrue(body.deviceId.isNotBlank())
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun `posting a message without auth is rejected`() = testApplication {
        application { module(database) }
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val response = client.post("/mailbox/some-recipient") {
            contentType(ContentType.Application.Json)
            setBody(MailboxSubmission(senderDeviceId = "x", kind = "MESSAGE", ciphertext = "abc"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `full round trip registers two devices sends a message pulls and acks it`() = testApplication {
        application { module(database) }
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val sender = client.post("/devices").body<DeviceRegistrationResponse>()
        val recipient = client.post("/devices").body<DeviceRegistrationResponse>()

        val submitResponse = client.post("/mailbox/${recipient.deviceId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${sender.token}")
            setBody(MailboxSubmission(senderDeviceId = sender.deviceId, kind = "MESSAGE", ciphertext = "ciphertext-abc"))
        }
        assertEquals(HttpStatusCode.Created, submitResponse.status)
        val messageId = submitResponse.body<MailboxSubmissionResponse>().messageId

        val pullResponse = client.get("/mailbox") {
            header(HttpHeaders.Authorization, "Bearer ${recipient.token}")
        }
        val items = pullResponse.body<List<MailboxItem>>()
        assertEquals(1, items.size)
        assertEquals("ciphertext-abc", items.first().ciphertext)

        val deleteResponse = client.delete("/mailbox/$messageId") {
            header(HttpHeaders.Authorization, "Bearer ${recipient.token}")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val pullAgain = client.get("/mailbox") {
            header(HttpHeaders.Authorization, "Bearer ${recipient.token}")
        }
        assertTrue(pullAgain.body<List<MailboxItem>>().isEmpty())
    }

    @Test
    fun `sending to an unknown recipient is rejected`() = testApplication {
        application { module(database) }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        val sender = client.post("/devices").body<DeviceRegistrationResponse>()

        val response = client.post("/mailbox/unknown-device") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${sender.token}")
            setBody(MailboxSubmission(senderDeviceId = sender.deviceId, kind = "MESSAGE", ciphertext = "x"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `sending with a spoofed sender id is rejected`() = testApplication {
        application { module(database) }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        val sender = client.post("/devices").body<DeviceRegistrationResponse>()
        val recipient = client.post("/devices").body<DeviceRegistrationResponse>()

        val response = client.post("/mailbox/${recipient.deviceId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${sender.token}")
            setBody(MailboxSubmission(senderDeviceId = "someone-else", kind = "MESSAGE", ciphertext = "x"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `rate limiter blocks excessive requests`() = testApplication {
        application { module(database, RateLimiter(capacity = 1, refillPerSecond = 0.0)) }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        val device = client.post("/devices").body<DeviceRegistrationResponse>()

        val first = client.get("/mailbox") { header(HttpHeaders.Authorization, "Bearer ${device.token}") }
        val second = client.get("/mailbox") { header(HttpHeaders.Authorization, "Bearer ${device.token}") }

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
    }
}
