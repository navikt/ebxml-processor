package no.nav.emottak.ebms.async.processing

import io.ktor.http.ContentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toKafkaHeaders
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
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import kotlin.uuid.Uuid

class PayloadMessageForwardingService(
    val sendInService: SendInService,
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val payloadRepository: PayloadRepository,
    val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    val ebmsPayloadProducer: EbmsMessageProducer,
    val eventRegistrationService: EventRegistrationService
) {

    suspend fun forwardMessageWithSyncResponse(payloadMessage: PayloadMessage) {
        val payloadMessageResponse = sendInService.sendIn(payloadMessage).let { sendInResponse ->
            PayloadMessage(
                requestId = sendInResponse.requestId,
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
        savePayloadsToDatabase(
            signedEbmsDocument.requestId.parseOrGenerateUuid(),
            signedEbmsDocument.messageHeader().messageData.messageId,
            signedEbmsDocument.attachments
        )
        sendResponseToTopic(signedEbmsDocument, validationResult.receiverEmailAddress)
        log.info(processedMessage.marker(), "Payload message response returned successfully")
    }

    suspend fun savePayloadsToDatabase(
        requestId: Uuid,
        messageId: String,
        attachments: List<Payload>
    ) {
        attachments.forEach { payload ->
            val asyncPayload = AsyncPayload(
                referenceId = requestId,
                contentId = payload.contentId,
                contentType = payload.contentType,
                content = payload.bytes
            )

            eventRegistrationService.runWithEvent(
                successEvent = EventType.PAYLOAD_SAVED_INTO_DATABASE,
                failEvent = EventType.ERROR_WHILE_SAVING_PAYLOAD_INTO_DATABASE,
                requestId = asyncPayload.referenceId,
                contentId = asyncPayload.contentId,
                messageId = messageId
            ) {
                payloadRepository.updateOrInsert(asyncPayload)
            }
        }
    }

    private suspend fun sendResponseToTopic(signedEbmsDocument: EbMSDocument, receiverEmailAddress: List<EmailAddress>) {
        eventRegistrationService.runWithEvent(
            successEvent = EventType.MESSAGE_PLACED_IN_QUEUE,
            failEvent = EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
            requestId = signedEbmsDocument.requestId.parseOrGenerateUuid(),
            messageId = signedEbmsDocument.messageHeader().messageData.messageId ?: "",
            eventData = Json.encodeToString(
                mapOf(EventDataType.QUEUE_NAME.value to config().kafkaPayloadProducer.topic)
            )
        ) {
            ebmsPayloadProducer.publishMessage(
                key = signedEbmsDocument.requestId,
                value = signedEbmsDocument.dokument.asByteArray(),
                headers = receiverEmailAddress.toKafkaHeaders() + signedEbmsDocument.messageHeader().toKafkaHeaders()
            )
        }
    }
}
