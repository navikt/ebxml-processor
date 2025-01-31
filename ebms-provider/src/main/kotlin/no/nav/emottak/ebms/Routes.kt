package no.nav.emottak.ebms

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.ebms.model.saveEbmsMessage
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.ebms.validation.MimeValidationException
import no.nav.emottak.ebms.validation.parseAsSoapFault
import no.nav.emottak.ebms.validation.validateMime
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveLoggableHeaderPairs
import java.util.UUID

fun Route.postEbmsSync(
    validator: DokumentValidator,
    processingService: ProcessingService,
    sendInService: SendInService,
    ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
): Route = post("/ebms/sync") {
    log.info("Receiving synchronous request")

    val ebMSDocument: EbMSDocument
    val loggableHeaders = call.request.headers.retrieveLoggableHeaderPairs()
    try {
        call.request.validateMime()
        ebMSDocument = call.receiveEbmsDokument()
        log.info(ebMSDocument.messageHeader().marker(loggableHeaders), "Melding mottatt")
    } catch (ex: MimeValidationException) {
        logger().error(
            call.request.headers.marker(),
            "Mime validation has failed: ${ex.message} Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        call.respond(HttpStatusCode.InternalServerError, ex.parseAsSoapFault())
        return@post
    } catch (ex: Exception) {
        logger().error(
            call.request.headers.marker(),
            "Unable to transform request into EbmsDokument: ${ex.message} " +
                "Message-Id ${call.request.header(SMTPHeaders.MESSAGE_ID)}",
            ex
        )
        // @TODO done only for demo fiks!
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
        return@post
    }

    val ebmsMessage = ebMSDocument.transform() as PayloadMessage
    ebmsMessageDetailsRepository.saveEbmsMessage(ebmsMessage)

    var signingCertificate: SignatureDetails? = null
    try {
        validator.validateIn(ebmsMessage)
            .let { validationResult ->
                processingService.processSyncIn(ebmsMessage, validationResult.payloadProcessing)
            }.let { processedMessage ->
                when (processedMessage.second) {
                    Direction.IN -> {
                        sendInService.sendIn(processedMessage.first).let {
                            PayloadMessage(
                                requestId = UUID.randomUUID().toString(),
                                messageId = UUID.randomUUID().toString(),
                                conversationId = it.conversationId,
                                cpaId = ebmsMessage.cpaId,
                                addressing = it.addressing,
                                payload = Payload(it.payload, ContentType.Application.Xml.toString()),
                                refToMessageId = it.messageId
                            )
                        }
                    }

                    else -> processedMessage.first
                }
            }.let { payloadMessage ->
                validator.validateOut(payloadMessage).let {
                    signingCertificate = it.payloadProcessing?.signingCertificate
                    Pair<PayloadMessage, PayloadProcessing?>(payloadMessage, it.payloadProcessing)
                }
            }.let { messageProcessing ->
                val processedMessage =
                    processingService.proccessSyncOut(messageProcessing.first, messageProcessing.second)
                Pair<PayloadMessage, PayloadProcessing?>(processedMessage, messageProcessing.second)
            }.let {
                call.respondEbmsDokument(
                    it.first.toEbmsDokument().also { ebmsDocument ->
                        ebmsDocument.signer(it.second!!.signingCertificate)
                    }
                )
                log.info(it.first.marker(), "Melding ferdig behandlet og svar returnert")
                return@post
            }
    } catch (ebmsException: EbmsException) {
        log.error(ebmsMessage.marker(), ebmsException.message, ebmsException)
        ebmsMessage.createFail(ebmsException.feil).toEbmsDokument().also {
            signingCertificate?.let { signatureDetails ->
                it.signer(signatureDetails)
            }
            log.info(ebmsMessage.marker(), "Created MessageError response")
            call.respondEbmsDokument(it)
            return@post
        }
    } catch (ex: Exception) {
        log.error(ebmsMessage.marker(), "Unknown error during message processing: ${ex.message}", ex)
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.parseAsSoapFault()
        )
    }
}
