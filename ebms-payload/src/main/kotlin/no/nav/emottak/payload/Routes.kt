package no.nav.emottak.payload

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.apprec.createNegativeApprec
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.payload.crypto.DecryptionException
import no.nav.emottak.payload.util.marshal
import no.nav.emottak.payload.util.unmarshal
import no.nav.emottak.util.marker

fun Route.postPayload() = post("/payload") {
    val request: PayloadRequest = call.receive(PayloadRequest::class)
    log.info(request.marker(), "Payload mottatt for prosessering <${request.payload.contentId}>")
    log.debug(request.marker(), "Payload mottatt for prosessering med steg: {}", request.processing.processConfig)

    runCatching {
        processor.process(request)
    }.onSuccess {
        log.info(request.marker(), "Payload prosessert OK <${request.payload.contentId}>")
        call.respond(it)
    }.onFailure { originalError ->
        log.error(request.marker(), "Payload prosessert med feil ${originalError.localizedMessage}", originalError)
        val apprecResponse = request.processing.processConfig.apprec &&
            originalError !is DecryptionException &&
            request.direction == Direction.IN

        runCatching {
            when (apprecResponse) {
                true -> {
                    log.info(request.marker(), "Oppretter negativ AppRec for payload <${request.payload.contentId}>")
                    val msgHead = unmarshal(
                        processor.getDecryptedAndDecompressedBytes(
                            request.payload.bytes,
                            request.processing.processConfig.kryptering,
                            request.processing.processConfig.komprimering
                        ),
                        MsgHead::class.java
                    )
                    val apprec = createNegativeApprec(msgHead, originalError as Exception)
                    Payload(marshal(apprec).toByteArray(), ContentType.Application.Xml.toString())
                }
                false -> null
            }
        }.onSuccess {
            call.respond(
                HttpStatusCode.BadRequest,
                PayloadResponse(
                    processedPayload = it,
                    error = Feil(ErrorCode.UNKNOWN, originalError.localizedMessage, "Error"),
                    apprec = it != null
                )
            )
        }.onFailure {
            log.error(request.marker(), "Opprettelse av negativ apprec feilet", it)
            call.respond(
                HttpStatusCode.BadRequest,
                PayloadResponse(
                    error = Feil(ErrorCode.UNKNOWN, it.localizedMessage, "Error")
                )
            )
        }
    }
}

fun Routing.registerHealthEndpoints(
    collectorRegistry: PrometheusMeterRegistry
) {
    get("/internal/health/liveness") {
        call.respondText("I'm alive! :)")
    }
    get("/internal/health/readiness") {
        call.respondText("I'm ready! :)")
    }
    get("/prometheus") {
        call.respond(collectorRegistry.scrape())
    }
}
