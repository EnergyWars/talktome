package com.wafflehq.talktome.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("TALKTOME_SERVER_PORT")?.toIntOrNull() ?: 8080
    val databasePath = System.getenv("TALKTOME_SERVER_DB_PATH") ?: "talktome-server.db"
    val database = MailboxDatabase("jdbc:sqlite:$databasePath")
    embeddedServer(Netty, port = port) {
        module(database)
    }.start(wait = true)
}

fun Application.module(database: MailboxDatabase, rateLimiter: RateLimiter = RateLimiter()) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "internal_error"))
        }
    }
    configureAuth(database)
    configureRouting(database, rateLimiter)
}
