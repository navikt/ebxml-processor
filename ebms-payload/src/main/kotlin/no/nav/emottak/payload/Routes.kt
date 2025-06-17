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
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.apprec.createNegativeApprec
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.payload.crypto.DecryptionException
import no.nav.emottak.payload.crypto.EncryptionException
import no.nav.emottak.payload.juridisklogg.JuridiskLoggException
import no.nav.emottak.payload.ocspstatus.SertifikatError
import no.nav.emottak.payload.util.CompressionException
import no.nav.emottak.payload.util.DecompressionException
import no.nav.emottak.payload.util.EventRegistrationService
import no.nav.emottak.payload.util.marshal
import no.nav.emottak.payload.util.unmarshal
import no.nav.emottak.util.marker
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.serialization.toEventDataJson

fun Route.postPayload(
    processor: Processor,
    eventRegistrationService: EventRegistrationService
) = post("/payload") {
    val request: PayloadRequest = call.receive(PayloadRequest::class)

    // TODO: Skal brukes i kall mot Event-logging:
    // val requestId = request.requestId

    log.info(request.marker(), "Payload mottatt for prosessering <${request.payload.contentId}>")
    log.debug(request.marker(), "Payload mottatt for prosessering med steg: {}", request.processing.processConfig)

    var juridiskLoggRecordId: String? = null
    runCatching {
        val processConfig = request.processing.processConfig

        if (processConfig.juridiskLogg) {
            juridiskLoggRecordId = processor.loggMessageToJuridiskLogg(request)
        }

        when (request.direction) {
            Direction.IN -> createIncomingPayloadResponse(request, processConfig, processor)
            Direction.OUT -> createOutgoingPayloadResponse(request, processor)
        }
    }.onSuccess {
        it.juridiskLoggRecordId = juridiskLoggRecordId

        if (it.error != null) {
            log.error(request.marker(), "Payload prosessert med kode ${it.error!!.code.description} og feil: ${it.error!!.descriptionText}", it.error)
            call.respond(HttpStatusCode.BadRequest, it)
        } else {
            log.info(request.marker(), "Payload prosessert OK <${request.payload.contentId}>")
            call.respond(it)
        }
    }.onFailure { error ->
        log.error(request.marker(), "Payload prosessert med feil: ${error.localizedMessage}", error)

        val eventType = when (error) {
            is JuridiskLoggException -> EventType.ERROR_WHILE_SAVING_MESSAGE_IN_JURIDISK_LOGG
            is EncryptionException -> EventType.MESSAGE_ENCRYPTION_FAILED
            is DecryptionException -> EventType.MESSAGE_DECRYPTION_FAILED
            is CompressionException -> EventType.MESSAGE_COMPRESSION_FAILED
            is DecompressionException -> EventType.MESSAGE_DECOMPRESSION_FAILED
            is SignatureException -> EventType.SIGNATURE_CHECK_FAILED
            is SertifikatError -> EventType.OCSP_CHECK_FAILED
            else -> EventType.UNKNOWN_ERROR_OCCURRED
        }

        eventRegistrationService.registerEvent(
            eventType,
            request,
            Exception(error).toEventDataJson()
        )

        call.respond(
            HttpStatusCode.BadRequest,
            PayloadResponse(
                error = Feil(ErrorCode.UNKNOWN, error.localizedMessage, "Error")
            )
        )
    }
}

private suspend fun createOutgoingPayloadResponse(request: PayloadRequest, processor: Processor) = PayloadResponse(
    processedPayload = processor.processOutgoing(request)
)

private suspend fun createIncomingPayloadResponse(
    request: PayloadRequest,
    processConfig: ProcessConfig,
    processor: Processor
): PayloadResponse {
    val readablePayload =
        processor.convertToReadablePayload(request, processConfig.kryptering, processConfig.komprimering).also {
            if (processConfig.kryptering) log.info(request.marker(), "Payload dekryptert")
            if (processConfig.komprimering) log.info(request.marker(), "Payload dekomprimert")
        }
    return try {
        PayloadResponse(
            processedPayload = processor.validateReadablePayload(
                request.marker(),
                readablePayload,
                request,
                processConfig
            ).also {
                if (processConfig.signering) log.info(request.marker(), "Payload signatur verifisert")
                if (processConfig.ocspSjekk) log.info(request.marker(), "Payload signatur ocsp sjekket")
            }
        )
    } catch (e: Exception) {
        log.error(request.marker(), "Feil ved validering av payload, creating AppRec or Error Payload in response instead", e)
        val errorPayload: Payload? = createNegativeAppRecOrErrorPayload(processConfig, request, readablePayload, e)
        PayloadResponse(
            processedPayload = errorPayload,
            error = Feil(ErrorCode.UNKNOWN, e.localizedMessage, "Error"),
            apprec = errorPayload != null
        )
    }
}

private fun createNegativeAppRecOrErrorPayload(
    processConfig: ProcessConfig,
    request: PayloadRequest,
    readablePayload: Payload,
    e: Exception
) = runCatching {
    when (processConfig.apprec) {
        true -> {
            log.info(request.marker(), "Oppretter negativ AppRec for payload <${request.payload.contentId}>")
            Payload(
                marshal(
                    createNegativeApprec(unmarshal(readablePayload.bytes, MsgHead::class.java), e)
                ).toByteArray(),
                ContentType.Application.Xml.toString()
            )
        }
        false -> null
    }
}.onFailure {
    log.error(request.marker(), "Opprettelse av negativ apprec feilet", it)
}.getOrThrow()

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
