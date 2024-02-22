package no.nav.emottak.ebms

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.ebxml.addressing
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.SendInResponse
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.melding.model.asErrorList
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import java.util.UUID

fun Route.postEbmsSyc(validator: DokumentValidator, processingService: ProcessingService, sendInService: SendInService): Route = post("/ebms/sync") {
    log.info("Recieving synchroneus reqyest")
    val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
    val ebMSDocument: EbMSDocument
    try {
        call.request.validateMime()
        ebMSDocument = call.receiveEbmsDokument()
    } catch (ex: MimeValidationException) {
        logger().error("Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}", ex)
        call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
        return@post
    } catch (ex: Exception) {
        logger().error("Unable to transform request into EbmsDokument: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}", ex)
        // @TODO done only for demo fiks!
        call.respond(HttpStatusCode.InternalServerError, MimeValidationException("Unable to transform request into EbmsDokument: ${ex.message}", ex).asParseAsSoapFault())
        return@post
    }

    // TODO gjøre dette bedre
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")

    val validationResult = validator.validateIn(ebMSDocument)
    if (!validationResult.valid()) {
        ebMSDocument
            .createFail(validationResult.error!!.asErrorList())
            .toEbmsDokument()
            .also {
                call.respondEbmsDokument(it)
                return@post
            }
    }

    val message = ebMSDocument.buildEbmMessage()
    var payloadResponse: PayloadResponse? = null
    try {
        if (!debug) {
            payloadResponse = processingService.processSync(message, validationResult.payloadProcessing)
        }
    } catch (ex: EbmsException) {
        logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        ebMSDocument
            .createFail(ex.errorCode.createEbxmlError())
            .toEbmsDokument()
            .also {
                call.respondEbmsDokument(it)
            }
        return@post
    } catch (ex: ServerResponseException) {
        logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        ebMSDocument
            .createFail(ErrorCode.UNKNOWN.createEbxmlError("Processing failed: ${ex.message}"))
            .toEbmsDokument()
            //  .signer(cpa.signatureDetails) //@Alexander After Testing T1 directly. It seams as the fail messages are not signert i sync
            .also {
                call.respondEbmsDokument(it)
                return@post
            }
    } catch (ex: ClientRequestException) {
        logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        ebMSDocument
            .createFail(ErrorCode.OTHER_XML.createEbxmlError("Processing failed: ${ex.message}"))
            .toEbmsDokument()
            //  .signer(cpa.signatureDetails) //@Alexander After Testing T1 directly. It seams as the fail messages are not signert i sync
            .also {
                call.respondEbmsDokument(it)
                return@post
            }
    } catch (ex: Exception) {
        logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        call.respond(HttpStatusCode.InternalServerError, "Feil ved prosessering av melding")
        return@post
    }

    val sendInResponse = sendInService.sendIn(message as EbmsPayloadMessage, message.messageHeader.addressing(), validationResult.ebmsProcessing!!, payloadResponse!!.processedPayload)
    val validateResult = validator.validateOut(UUID.randomUUID().toString(), ValidationRequest(UUID.randomUUID().toString(), message.messageHeader.cpaId, sendInResponse.conversationId, sendInResponse.addressing))
    val processingResponse = processingService.proccessSyncOut(sendInResponse, validationResult.payloadProcessing!!)
    println("Send in response: " + Json.encodeToString(SendInResponse.serializer(), sendInResponse))
    println("not processed message" + Json.encodeToString(ValidationResult.serializer(), validateResult))
    println("Processed message" + String(processingResponse.processedPayload))
    this.call.respondText(String(sendInResponse.payload))
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
            logger().error("Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}", ex)
            call.respond(HttpStatusCode.InternalServerError, ex.asParseAsSoapFault())
            return@post
        } catch (ex: Exception) {
            logger().error("Unable to transform request into EbmsDokument: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}", ex)
            // @TODO done only for demo fiks!
            call.respond(HttpStatusCode.InternalServerError, MimeValidationException("Unable to transform request into EbmsDokument: ${ex.message}", ex).asParseAsSoapFault())
            return@post
        }

        // TODO gjøre dette bedre
        val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")

        val validationResult = validator.validateIn(ebMSDocument)
        if (!validationResult.valid()) {
            ebMSDocument
                .createFail(validationResult.error!!.asErrorList())
                .toEbmsDokument()
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        }

        val message = ebMSDocument.buildEbmMessage()
        try {
            if (!debug) {
                processingService.processAsync(message, validationResult.payloadProcessing)
            }
        } catch (ex: EbmsException) {
            logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(ex.errorCode.createEbxmlError())
                .toEbmsDokument()
                .also {
                    call.respondEbmsDokument(it)
                }
            return@post
        } catch (ex: ServerResponseException) {
            logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(ErrorCode.UNKNOWN.createEbxmlError("Processing failed: ${ex.message}"))
                .toEbmsDokument()
                //  .signer(cpa.signatureDetails) //@TODO hva skjer hvis vi klarer ikke å hente signature details ?
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        } catch (ex: ClientRequestException) {
            logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
            ebMSDocument
                .createFail(ErrorCode.OTHER_XML.createEbxmlError("Processing failed: ${ex.message}"))
                .toEbmsDokument()
                //  .signer(cpa.signatureDetails) //@TODO hva skjer hvis vi klarer ikke å hente signature details ?
                .also {
                    call.respondEbmsDokument(it)
                    return@post
                }
        } catch (ex: Exception) {
            logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, "Feil ved prosessering av melding")
            return@post
        }

        // call payload processor
        if (message is EbmsPayloadMessage) {
            log.info(message.messageHeader.marker(), "Payload Processed, Generating Acknowledgement...")
            message.createAcknowledgment().toEbmsDokument().also {
                call.respondEbmsDokument(it)
                return@post
            }
        }
        log.info(message.messageHeader.marker(), "Successfuly processed Signal Message")
        call.respondText("Processed")
    }
