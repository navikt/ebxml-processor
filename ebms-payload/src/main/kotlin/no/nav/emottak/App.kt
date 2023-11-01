package no.nav.emottak

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import no.nav.emottak.melding.Processor
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

val processor = Processor()
internal val log = LoggerFactory.getLogger("no.nav.emottak.payload.App")
fun main() {
    embeddedServer(Netty, port = 8080) {
        serverSetup()
    }.start(wait = true)
}

private fun Application.serverSetup() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().startsWith("/internal") }
    }

    install(ContentNegotiation) {
        json()
    }
    routing {
        registerHealthEndpoints()

        get("/payload/test") {
            val testByteArray = this::class.java.classLoader.getResource("xml/test.xml").readBytes()
            val request = PayloadRequest(
                header = Header(
                    messageId = UUID.randomUUID().toString(),
                    conversationId = UUID.randomUUID().toString(),
                    cpaId = UUID.randomUUID().toString(),
                    to = Party(
                        partyType = "HER",
                        partyId = "8141253",
                        role = "mottaker"
                    ),
                    from = Party(
                        partyType = "HER",
                        partyId = "54321",
                        role = "sender"
                    ),
                    service = "melding",
                    action = "send"
                ),
                payload = testByteArray
            )
            val response = processor.processOutgoing(request)
            call.respond(response)
        }

        post("/payload") {
            val request: PayloadRequest = call.receive(PayloadRequest::class)
            runCatching {
                processor.process(request)
            }.onSuccess {
                call.respond(it)
            }.onFailure {
                log.warn(request.header.marker(), it.message, it)
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }

    }
}
