package no.nav.emottak.payload

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
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.apprec.createNegativeApprec
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.payload.util.marshal
import no.nav.emottak.payload.util.unmarshal
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

val processor = Processor()
internal val log = LoggerFactory.getLogger("no.nav.emottak.payload")
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
                log.info(request.marker(), "Payload ${request.payloadId} mottatt for prosessering")
                processor.process(request)
            }.onSuccess {
                log.info(request.marker(), "Payload ${request.payloadId} prosessert OK")
                call.respond(it)
            }.onFailure {
                val processedPayload: ByteArray = try {
                    when (request.processing.processConfig?.apprec) {
                        true -> {
                            log.info(request.marker(), "Oppretter negativ AppRec for payload ${request.payloadId}")
                            val msgHead = unmarshal(request.payload, MsgHead::class.java)
                            val apprec = createNegativeApprec(msgHead, it as Exception)
                            marshal(apprec).toByteArray()
                        }
                        else -> request.payload
                    }
                } catch (e: Exception) {
                    log.error(request.marker(), "Opprettelse av apprec feilet", e)
                    request.payload
                }
                val response = PayloadResponse(
                    payloadId = request.payloadId,
                    processedPayload = processedPayload,
                    error = Feil(ErrorCode.UNKNOWN, it.localizedMessage, "Error")
                )
                log.error(request.marker(), "Payload ${request.payloadId} prosessert med feil: ${it.message}", it)
                call.respond(HttpStatusCode.BadRequest, response)
            }
        }
    }
}
