package no.nav.emottak.ebms

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import no.nav.emottak.constants.EbXMLConstants
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.ebxml.createResponseHeader
import no.nav.emottak.ebms.ebxml.messageHeader
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.EbmsAttachment
import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.ebms.model.Payload
import no.nav.emottak.ebms.model.buildEbmMessage
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.ebms.validation.asParseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.ebms.xml.marshal
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.PayloadProcessing
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.melding.model.SendInResponse
import no.nav.emottak.melding.model.SignatureDetails
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
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory
import java.io.StringReader
import java.util.*


open class EbmsMessage(
    open val requestId:String,
    open val messageId: String,
    open val conversationId:String,
    open val cpaId:String,
    open val addressing: Addressing,
    val dokument: Document? = null,
    open val refToMessageId: String? =null,
)  {

    open fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator().validate(signatureDetails, this.dokument!!, listOf())
        no.nav.emottak.ebms.model.log.info("Signatur OK")
    }


    open fun createFail(errorList: List<Feil>): EbmsFail {
         return EbmsFail(requestId,UUID.randomUUID().toString(),this.messageId,this.conversationId,this.cpaId,this.addressing.copy(to = addressing.from,from = addressing.to),errorList)
    }
}


data class PayloadMessage(
    override val requestId: String,
    override val messageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val payload: Payload,
    val document: Document? = null,
    override  val refToMessageId:String? = null
) : EbmsMessage(
    requestId, messageId, conversationId, cpaId,
    addressing, document,refToMessageId
) {
    lateinit  var respondTo:Addressing
    lateinit  var ebmsProcessing: EbmsProcessing
    var payloadProcessing: PayloadProcessing? =null
    lateinit  var processedPayload : ByteArray
    lateinit  var feil: List<Feil>


    fun sendIn(sendInService: (EbmsMessage) -> SendInResponse) {
        val sendInResponse = sendInService.invoke(this)
        this.processedPayload = sendInResponse.payload
        this.respondTo = sendInResponse.addressing
    }

    fun valid() : Boolean {
        return !::feil.isInitialized || (::feil.isInitialized && feil.isEmpty())
    }
    /*
    override fun cpaValidate(cpaValidator: (EbmsMessage) -> ValidationResult) : ValidatedMessage {
            val validationResult = cpaValidator.invoke(this)
            if (validationResult.valid()) {
                return ValidatedMessage(this,validationResult.ebmsProcessing!!,validationResult.payloadProcessing!!)
            }
            else this.createFail(validationResult.error!!)
    }

     */
    override fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator().validate(signatureDetails, this.dokument!!, listOf(payload!!))
        no.nav.emottak.ebms.model.log.info("Signatur OK")
    }



}


class EbmsFail(
    requestId: String,
    messageId: String,
    override val refToMessageId: String,
    conversationId: String,
    cpaId: String,
    addressing: Addressing,
    val feil: List<Feil>,
    document: Document? = null
) : EbmsMessage(
    requestId, messageId, conversationId, cpaId,
    addressing, document,refToMessageId
) {

    fun toEbmsDokument(): EbMSDocument {
        val messageHeader = this.createResponseHeader(newAction = EbXMLConstants.MESSAGE_ERROR_ACTION, newService = EbXMLConstants.EBMS_SERVICE_URI)
        no.nav.emottak.ebms.model.log.warn(messageHeader.marker(), "Oppretter ErrorList")
        return ObjectFactory().createEnvelope()!!.also {
            it.header = Header().also {
                it.any.add(messageHeader)
                it.any.add(this.feil.asErrorList())
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            no.nav.emottak.ebms.model.log.info(messageHeader.marker(), "Signerer ErrorList (TODO)")
            // @TODO   val signatureDetails = runBlocking {  getPublicSigningDetails(this@EbMSMessageError.messageHeader) }
            EbMSDocument(UUID.randomUUID().toString(), it, emptyList())
            // @TODO      .signer(signatureDetails)
        }
    }
}

class Acknowledgment(requestId: String,
                     messageId: String,
                     refToMessageId:String,
                     conversationId: String,
                     cpaId: String,
                     addressing: Addressing,
                     document: Document? = null) : EbmsMessage(requestId,messageId, conversationId,cpaId,
    addressing,document
)


fun Route.postEbmsSyc(validator: DokumentValidator, processingService: ProcessingService, sendInService: SendInService): Route = post("/ebms/sync") {
    log.info("Recieving synchroneus reqyest")

    val debug: Boolean = call.request.header("debug")?.isNotBlank() ?: false
    val ebMSDocument: EbMSDocument
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    try {
        call.request.validateMime()
        ebMSDocument = call.receiveEbmsDokument()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")
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


    val ebmsMessage = ebMSDocument.transform() as PayloadMessage
    validator.validateIn2(ebmsMessage)

    validator.validateIn2(ebmsMessage)
if (!ebmsMessage.valid()) {
        ebmsMessage
            .createFail(ebmsMessage.feil)
            .toEbmsDokument()
            .also {
                call.respondEbmsDokument(it)
                return@post
            }
    }


    var payloadResponse: PayloadResponse? = null
    try {
        if (!debug) {
            payloadResponse = processingService.processSync2(ebmsMessage)
        }
    } catch (ex: EbmsException) {
        // @TODO fix logger().error(ebmsMessage.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        logger().error("Processing failed: ${ex.message}", ex)
        ebmsMessage
            .createFail(listOf(Feil(ex.errorCode,ex.errorCode.description)))
            .toEbmsDokument()
            .also {
                call.respondEbmsDokument(it)
            }
        return@post
    } catch (ex: ServerResponseException) {
        // @TODO fix  logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        logger().error("Processing failed: ${ex.message}", ex)
        ebmsMessage
            .createFail(listOf(Feil(ErrorCode.UNKNOWN,"Processing failed: ${ex.message}")))
            .toEbmsDokument()
            //  .signer(cpa.signatureDetails) //@Alexander After Testing T1 directly. It seams as the fail messages are not signert i sync
            .also {
                call.respondEbmsDokument(it)
                return@post
            }
    } catch (ex: ClientRequestException) {
        // @TODO fix logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        logger().error("Processing failed: ${ex.message}", ex)
        ebmsMessage
            .createFail(listOf(Feil(ErrorCode.OTHER_XML,"Processing failed: ${ex.message}")))
            .toEbmsDokument()
            //  .signer(cpa.signatureDetails) //@Alexander After Testing T1 directly. It seams as the fail messages are not signert i sync
            .also {
                call.respondEbmsDokument(it)
                return@post
            }
    } catch (ex: Exception) {
      // @TODO fix me  logger().error(message.messageHeader.marker(loggableHeaders), "Processing failed: ${ex.message}", ex)
        logger().error("Processing failed: ${ex.message}", ex)
        call.respond(HttpStatusCode.InternalServerError, "Feil ved prosessering av melding")
        return@post
    }

    //var sendInResponse = processedMessage?.send(sendInService::sendIn2)
     val sendInResponse = sendInService.sendIn(ebmsMessage as PayloadMessage, ebmsMessage.ebmsProcessing, payloadResponse!!.processedPayload)
    val validationRequest = ValidationRequest(UUID.randomUUID().toString(), sendInResponse.conversationId, ebmsMessage.cpaId, sendInResponse.addressing)
    println("Validation request is: " + Json.encodeToString(ValidationRequest.serializer(), validationRequest))
    val validateResult = validator.validateOut(UUID.randomUUID().toString(), validationRequest)

    val processingResponse = processingService.proccessSyncOut(sendInResponse, validateResult.payloadProcessing!!)

    val responseMessage = PayloadMessage(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        sendInResponse.conversationId,
        validationRequest.cpaId,
        validationRequest.addressing,
        Payload(sendInResponse.payload, "text/xml", UUID.randomUUID().toString()),
        refToMessageId = ebmsMessage.messageId
    )

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
        this.any.add(Manifest().apply {
            this.reference.add(
                Reference().apply {
                    this.href = "cid:"+attachmentUid
                    this.type = "simple"
                }
            )
        })
    }
    val dokument = getDocumentBuilder().parse(InputSource(StringReader( marshal(envelope))))
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
