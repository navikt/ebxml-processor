package no.nav.emottak.ebms

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.Payload
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import java.util.*

fun Route.postEbmsSyc(
    validator: DokumentValidator,
    processingService: ProcessingService,
    sendInService: SendInService
): Route = post("/ebms/sync") {
    log.info("Recieving synchroneus reqyest")

    val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
    val ebMSDocument: EbMSDocument
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    try {
        call.request.validateMime()
        ebMSDocument = call.receiveEbmsDokument()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")
    } catch (ex: MimeValidationException) {
        logger().error(
            "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
        return@post
    } catch (ex: Exception) {
        logger().error(
            "Unable to transform request into EbmsDokument: ${ex.message} Message-Id ${
            call.request.header(
                SMTPHeaders.MESSAGE_ID
            )
            }",
            ex
        )
        // @TODO done only for demo fiks!
        call.respond(
            HttpStatusCode.InternalServerError,
            MimeValidationException(
                "Unable to transform request into EbmsDokument: ${ex.message}",
                ex
            ).asParseAsSoapFault()
        )
        return@post
    }

    val ebmsMessage = ebMSDocument.transform() as PayloadMessage
    try {
        validator.validateIn(ebmsMessage)
            .let { validationResult ->
                processingService.processSyncIn(ebmsMessage, validationResult.payloadProcessing)
            }.let { processedMessage ->
                sendInService.sendIn(processedMessage).let {
                    PayloadMessage(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        it.conversationId,
                        ebmsMessage.cpaId,
                        it.addressing,
                        Payload(it.payload, "text/xml", UUID.randomUUID().toString())
                    )
                }
            }.let { payloadMessage ->
                validator.validateOut(payloadMessage).let {
                    Pair<PayloadMessage, PayloadProcessing?>(payloadMessage, it.payloadProcessing)
                }
            }.let { messageProcessing ->
                processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
            }.let {
                call.respondEbmsDokument(it.toEbmsDokument())
                return@post
            }
    } catch (ebmsException: EbmsException) {
        ebmsMessage.createFail(ebmsException.feil).toEbmsDokument().also {
            call.respondEbmsDokument(it)
            return@post
        }
    }
}

fun Route.postEbmsAsync(validator: DokumentValidator, processingService: ProcessingService): Route =
    post("/ebms") {
        // KRAV 5.5.2.1 validate MIME
        val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
        val ebMSDocument: EbMSDocument
        try {
            call.request.validateMime()
            ebMSDocument = call.receiveEbmsDokument()
        } catch (ex: MimeValidationException) {
            logger().error(
                "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
                ex
            )
            call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
            return@post
        } catch (ex: Exception) {
            logger().error(
                "Unable to transform request into EbmsDokument: ${ex.message} Message-Id ${
                call.request.header(
                    SMTPHeaders.MESSAGE_ID
                )
                }",
                ex
            )
            // @TODO done only for demo fiks!
            call.respond(
                HttpStatusCode.InternalServerError,
                MimeValidationException(
                    "Unable to transform request into EbmsDokument: ${ex.message}",
                    ex
                ).asParseAsSoapFault()
            )
            return@post
        }

        // TODO gjøre dette bedre
        val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
        val ebmsMessage = ebMSDocument.transform()

        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")
        try {
            validator
                .validateIn(ebmsMessage)
                .also {
                    processingService.processAsync(ebmsMessage, it.payloadProcessing)
                }
            if (ebmsMessage !is PayloadMessage) {
                log.info(ebMSDocument.messageHeader().marker(), "Successfuly processed Signal Message")
                call.respondText("Processed")
                return@post
            }
            log.info(ebMSDocument.messageHeader().marker(), "Payload Processed, Generating Acknowledgement...")
            ebmsMessage.createAcknowledgment().toEbmsDokument().also {
                call.respondEbmsDokument(it)
                return@post
            }
        } catch (ex: EbmsException) {
            ebmsMessage.createFail(ex.feil).toEbmsDokument().also {
                call.respondEbmsDokument(it)
                return@post
            }
        }
    }
