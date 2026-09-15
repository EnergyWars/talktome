package com.wafflehq.talktome.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer.bearer
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

data class DevicePrincipal(val deviceId: String)

private const val DEVICE_AUTH = "device-auth"

fun Application.configureAuth(database: MailboxDatabase) {
    install(Authentication) {
        bearer(DEVICE_AUTH) {
            realm = "talktome"
            authenticate { credential ->
                database.deviceIdForToken(credential.token)?.let { DevicePrincipal(it) }
            }
        }
    }
}

fun Application.configureRouting(database: MailboxDatabase, rateLimiter: RateLimiter) {
    routing {
        post("/devices") {
            call.respond(database.registerDevice())
        }

        authenticate(DEVICE_AUTH) {
            post("/mailbox/{recipientDeviceId}") {
                val principal = call.principal<DevicePrincipal>()!!
                if (!rateLimiter.tryConsume(principal.deviceId)) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited"))
                    return@post
                }
                val recipientDeviceId = call.parameters["recipientDeviceId"]
                if (recipientDeviceId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("missing_recipient"))
                    return@post
                }
                if (!database.deviceExists(recipientDeviceId)) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("unknown_recipient"))
                    return@post
                }
                val submission = call.receive<MailboxSubmission>()
                if (submission.senderDeviceId != principal.deviceId) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("sender_mismatch"))
                    return@post
                }
                val messageId = database.enqueue(
                    recipientDeviceId = recipientDeviceId,
                    senderDeviceId = principal.deviceId,
                    kind = submission.kind,
                    ciphertext = submission.ciphertext,
                )
                call.respond(HttpStatusCode.Created, MailboxSubmissionResponse(messageId))
            }

            get("/mailbox") {
                val principal = call.principal<DevicePrincipal>()!!
                if (!rateLimiter.tryConsume(principal.deviceId)) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited"))
                    return@get
                }
                call.respond(database.pending(principal.deviceId))
            }

            delete("/mailbox/{messageId}") {
                val principal = call.principal<DevicePrincipal>()!!
                val messageId = call.parameters["messageId"]
                if (messageId == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("missing_message_id"))
                    return@delete
                }
                if (database.delete(messageId, principal.deviceId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("unknown_message"))
                }
            }
        }
    }
}
