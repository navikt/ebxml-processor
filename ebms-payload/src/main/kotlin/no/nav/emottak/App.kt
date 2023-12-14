package no.nav.emottak

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.emottak.melding.Processor
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

val processor = Processor()
internal val log = LoggerFactory.getLogger("no.nav.emottak.payload.App")
fun main() {
    embeddedServer(Netty, port = 8080) {
        serverSetup()
    }.start(wait = true)
}

private fun Application.serverSetup() {
    install(ContentNegotiation) {
        json()
    }
    routing {
        registerHealthEndpoints()

        post("/payload") {
            val request: PayloadRequest = call.receive(PayloadRequest::class)
            runCatching {
                log.info(request.header.marker(), "Payload ${request.payloadId} mottatt for prosessering")
                processor.process(request)
            }.onSuccess {
                log.info(request.header.marker(), "Payload ${request.payloadId} prosessert OK")
                call.respond(it)
            }.onFailure {
                log.error(request.header.marker(), "Payload ${request.payloadId} prosessert med feil: ${it.message}", it)
                call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
            }
        }

    }
}
