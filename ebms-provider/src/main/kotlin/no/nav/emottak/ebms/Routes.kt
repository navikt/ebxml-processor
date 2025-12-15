package no.nav.emottak.ebms

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.EventRegistrationService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.convertToSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.serialization.toEventDataJson
import kotlin.uuid.Uuid

fun Route.postEbmsSync(
    cpaValidationService: CPAValidationService,
    processingService: ProcessingService,
    sendInService: SendInService,
    eventRegistrationService: EventRegistrationService
): Route = post("/ebms/sync") {
    log.info("Receiving synchronous request")

    val ebmsDocument = getEbmsDocument(call, eventRegistrationService) ?: return@post
    val ebmsMessage = ebmsDocument.transform() as PayloadMessage

    var signingCertificate: SignatureDetails? = null
    try {
        cpaValidationService.validateIncomingMessage(ebmsMessage)
            .let { validationResult ->
                val partnerId: Long? = validationResult.partnerId

                processingService.processSyncIn(ebmsMessage, validationResult.payloadProcessing)
                    .let { processedMessage ->
                        when (processedMessage.second) {
                            Direction.IN -> {
                                sendInService.sendIn(processedMessage.first, partnerId).let {
                                    PayloadMessage(
                                        requestId = Uuid.random().toString(),
                                        messageId = Uuid.random().toString(),
                                        conversationId = it.conversationId,
                                        cpaId = ebmsMessage.cpaId,
                                        addressing = it.addressing,
                                        payload = Payload(it.payload, ContentType.Application.Xml.toString()),
                                        refToMessageId = it.messageId,
                                        duplicateElimination = ebmsMessage.duplicateElimination
                                    )
                                }
                            }

                            else -> processedMessage.first
                        }
                    }
            }.let { payloadMessage ->
                cpaValidationService.validateOutgoingMessage(payloadMessage).let {
                    signingCertificate = it.payloadProcessing?.signingCertificate
                    Pair<PayloadMessage, PayloadProcessing?>(payloadMessage, it.payloadProcessing)
                }
            }.let { messageProcessing ->
                val processedMessage =
                    processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
                Pair<PayloadMessage, PayloadProcessing?>(processedMessage, messageProcessing.second)
            }.let {
                val ebmsDocument = it.first.toEbmsDokument()
                call.respondEbmsDokument(
                    ebmsDocument.also { ebmsDocument ->
                        ebmsDocument.signer(it.second!!.signingCertificate)
                    }
                )
                log.info(it.first.marker(), "Melding ferdig behandlet og svar returnert")
                eventRegistrationService.registerEvent(
                    EventType.MESSAGE_SENT_VIA_HTTP,
                    it.first.toEbmsDokument()
                )
                return@post
            }
    } catch (ebmsException: EbmsException) {
        log.error(ebmsMessage.marker(), ebmsException.message, ebmsException)
        ebmsMessage.createMessageError(ebmsException.feil).toEbmsDokument().also {
            signingCertificate?.let { signatureDetails ->
                it.signer(signatureDetails)
            }
            log.info(ebmsMessage.marker(), "Created MessageError response")

            eventRegistrationService.registerEvent(
                EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_HTTP,
                ebmsDocument,
                ebmsException.toEventDataJson()
            )

            call.respondEbmsDokument(it)
            return@post
        }
    } catch (ex: Exception) {
        log.error(ebmsMessage.marker(), ex.message ?: "Unknown error during message processing", ex)

        eventRegistrationService.registerEvent(
            EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_HTTP,
            ebmsDocument,
            ex.toEventDataJson()
        )

        call.respond(
            HttpStatusCode.InternalServerError,
            ex.convertToSoapFault()
        )
    }
}

private suspend fun getEbmsDocument(
    call: RoutingCall,
    eventRegistrationService: EventRegistrationService
): EbmsDocument? {
    val ebmsDocument: EbmsDocument
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    try {
        call.request.validateMime()
        ebmsDocument = call.receiveEbmsDokument()
        log.info(ebmsDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")

        eventRegistrationService.registerEventMessageDetails(ebmsDocument)
        eventRegistrationService.registerEvent(
            EventType.MESSAGE_RECEIVED_VIA_HTTP,
            ebmsDocument
        )
    } catch (ex: MimeValidationException) {
        logger().error(
            call.request.headers.marker(),
            "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        call.respond(HttpStatusCode.InternalServerError, ex.convertToSoapFault())
        return null
    } catch (ex: Exception) {
        logger().error(
            call.request.headers.marker(),
            "Unable to transform request into EbmsDokument: ${ex.message} " +
                "Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.convertToSoapFault()
        )
        return null
    }
    return ebmsDocument
}
