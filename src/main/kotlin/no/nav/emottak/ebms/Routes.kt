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
import no.nav.emottak.ebms.ebxml.messageHeader
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbmsAttachment
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.ebms.xml.marshal
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.SendInResponse
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.melding.model.asErrorList
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.From
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Manifest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageData
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.PartyId
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Reference
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Service
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SyncReply
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.To
import org.xml.sax.InputSource
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header
import java.io.StringReader
import java.util.*

open class EbmsMessage(
    val messageId: String,
    val conversationId: String,
    val addressing: Addressing? = null
)

class PayloadMessage private constructor(messageId: String, conversationId: String, addressing: Addressing? = null, payload: ByteArray? = null) : EbmsMessage(
    messageId,
    conversationId,
    addressing
)

class EbmsFail(messageId: String, conversationId: String, addressing: Addressing, feil: List<Feil>) : EbmsMessage(
    messageId,
    conversationId,
    addressing
)

class Acknowledgment(messageId: String, conversationId: String, addressing: Addressing, feil: List<Feil>) : EbmsMessage(
    messageId,
    conversationId,
    addressing
)

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
    val validationRequest = ValidationRequest(UUID.randomUUID().toString(), sendInResponse.conversationId, message.messageHeader.cpaId, sendInResponse.addressing)
    println("Validation request is: " + Json.encodeToString(ValidationRequest.serializer(), validationRequest))
    val validateResult = validator.validateOut(UUID.randomUUID().toString(), validationRequest)
    val processingResponse = processingService.proccessSyncOut(sendInResponse, validationResult.payloadProcessing!!)
    println("Send in response: " + Json.encodeToString(SendInResponse.serializer(), sendInResponse))
    println("not processed message" + Json.encodeToString(ValidationResult.serializer(), validateResult))
    println("Processed message" + String(processingResponse.processedPayload))
    val ebmsResponseHeader = responseHeader(validationRequest, sendInResponse)
    val ebMSDocumentResponse = createEbmsDocument(ebmsResponseHeader, sendInResponse.payload)

    this.call.respondEbmsDokument(ebMSDocumentResponse)
}

fun createEbmsDocument(ebxmlDokument: Header, payload: ByteArray): EbMSDocument {
    val envelope = Envelope()
    val attachmentUid = UUID.randomUUID().toString()
    envelope.header = ebxmlDokument

    envelope.body = Body().apply {
        this.any.add(
            Manifest().apply {
                this.reference.add(
                    Reference().apply {
                        this.href = "cid:" + attachmentUid
                        this.type = "simple"
                    }
                )
            }
        )
    }
    val dokument = getDocumentBuilder().parse(InputSource(StringReader(marshal(envelope))))
    return EbMSDocument(UUID.randomUUID().toString(), dokument, listOf(EbmsAttachment(payload, "application/xml", attachmentUid)))
}

fun responseHeader(validationRequest: ValidationRequest, sendInResponse: SendInResponse): Header {
    val messageData = MessageData().apply {
        this.messageId = UUID.randomUUID().toString()
        this.refToMessageId = validationRequest.messageId
        this.timestamp = Date()
    }
    val from = From().apply {
        this.role = sendInResponse.addressing.from.role
        this.partyId.addAll(
            sendInResponse.addressing.from.partyId.map {
                PartyId().apply {
                    this.type = it.type
                    this.value = it.value
                }
            }.toList()
        )
    }
    val to = To().apply {
        this.role = sendInResponse.addressing.from.role
        this.partyId.addAll(
            sendInResponse.addressing.to.partyId.map {
                PartyId().apply {
                    this.type = it.type
                    this.value = it.value
                }
            }.toList()
        )
    }
    val syncReply = SyncReply().apply {
        this.actor = "http://schemas.xmlsoap.org/soap/actor/next"
        this.isMustUnderstand = true
        this.version = "2.0"
    }
    val messageHeader = MessageHeader().apply {
        this.from = from
        this.to = to
        this.cpaId = validationRequest.cpaId
        this.conversationId = validationRequest.conversationId
        this.service = Service().apply {
            this.value = sendInResponse.addressing.service
            this.type = "string"
        }
        this.action = sendInResponse.addressing.action
        this.messageData = messageData
    }

    return Header().apply {
        this.any.addAll(
            listOf(messageHeader, syncReply)
        )
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
