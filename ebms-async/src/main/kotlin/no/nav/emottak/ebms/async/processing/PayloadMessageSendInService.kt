package no.nav.emottak.ebms.async.processing

import io.ktor.http.ContentType
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.util.toHeaders
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asByteArray
import kotlin.uuid.Uuid

class PayloadMessageSendInService(
    val sendInService: SendInService,
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val payloadRepository: PayloadRepository,
    val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val ebmsPayloadProducer: EbmsMessageProducer
) {

    suspend fun forwardMessageWithSyncResponse(payloadMessage: PayloadMessage) {
        try {
            val payloadMessageResponse = sendInService.sendIn(payloadMessage).let { sendInResponse ->
                PayloadMessage(
                    requestId = Uuid.random().toString(),
                    messageId = Uuid.random().toString(),
                    conversationId = sendInResponse.conversationId,
                    cpaId = payloadMessage.cpaId,
                    addressing = sendInResponse.addressing,
                    payload = Payload(sendInResponse.payload, ContentType.Application.Xml.toString()),
                    refToMessageId = payloadMessage.messageId
                )
            }

            val validationResult = cpaValidationService.validateOutgoingMessage(payloadMessageResponse)
            val processedMessage = processingService.proccessSyncOut(
                payloadMessageResponse,
                validationResult.payloadProcessing
            )

            ebmsMessageDetailsRepository.saveEbmsMessage(processedMessage)
            val signedEbmsDocument = processedMessage.toEbmsDokument()
                .signer(validationResult.payloadProcessing!!.signingCertificate)

            persistPayloads(signedEbmsDocument.requestId, signedEbmsDocument.attachments)
            sendResponseToTopic(signedEbmsDocument, validationResult.receiverEmailAddress)
            log.info(processedMessage.marker(), "Payload message response returned successfully")
        } catch (e: Exception) {
            log.error(payloadMessage.marker(), "Error processing asynchronous payload response", e)
        }
    }

    private suspend fun sendResponseToTopic(signedEbmsDocument: EbMSDocument, receiverEmailAddress: List<EmailAddress>) {
        ebmsPayloadProducer.publishMessage(
            key = signedEbmsDocument.requestId,
            value = signedEbmsDocument.dokument.asByteArray(),
            headers = receiverEmailAddress.toHeaders()
        )
    }

    private fun persistPayloads(requestId: String, attachments: List<Payload>) {
        attachments.forEach { payload ->
            payloadRepository.updateOrInsert(
                AsyncPayload(
                    referenceId = requestId,
                    contentId = payload.contentId,
                    contentType = payload.contentType,
                    content = payload.bytes
                )
            )
        }
    }
}
