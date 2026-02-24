package no.nav.emottak.ebms.async.processing

import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAck
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toAddressKafkaHeader
import no.nav.emottak.ebms.async.util.toKafkaHeaders
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.util.toByteArray
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.apache.kafka.common.header.Header
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import kotlin.uuid.Uuid

class PayloadMessageForwardingService(
    val sendInService: SendInService,
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val payloadRepository: PayloadRepository,
    val ebmsPayloadProducer: EbmsMessageProducer,
    val eventRegistrationService: EventRegistrationService,
    val messagePendingAckRepository: MessagePendingAckRepository
) {

    suspend fun forwardMessageWithSyncResponse(payloadMessage: PayloadMessage) {
        when (val service = payloadMessage.addressing.service) {
            "HarBorgerFrikortMengde", "Inntektsforesporsel", "Trekkopplysning" -> {
                log.debug(payloadMessage.marker(), "Starting SendIn for $service")
                sendInService.sendIn(payloadMessage).let { sendInResponse ->
                    PayloadMessage(
                        requestId = sendInResponse.requestId,
                        messageId = sendInResponse.messageId,
                        conversationId = sendInResponse.conversationId,
                        cpaId = payloadMessage.cpaId,
                        addressing = sendInResponse.addressing,
                        payload = Payload(sendInResponse.payload, ContentType.Application.Xml.toString()),
                        refToMessageId = payloadMessage.messageId,
                        duplicateElimination = payloadMessage.duplicateElimination,
                        ackRequested = true
                    )
                }.let { payloadMessageResponse ->
                    returnMessageResponse(payloadMessageResponse)
                }
            }
            else -> {
                log.debug(payloadMessage.marker(), "Skipping SendIn for $service")
            }
        }
    }

    // Used to send OUT a response from NAV
    suspend fun returnMessageResponse(payloadMessage: PayloadMessage) {
        val validationResult = cpaValidationService.validateOutgoingMessage(payloadMessage)
        val processedMessage = processingService.proccessSyncOut(
            payloadMessage,
            validationResult.payloadProcessing
        )

        val signedEbmsDocument = processedMessage.toEbmsDokument()
            .signer(validationResult.payloadProcessing!!.signingCertificate)
        savePayloadsToDatabase(
            signedEbmsDocument.requestId.parseOrGenerateUuid(),
            signedEbmsDocument.messageHeader().messageData.messageId,
            signedEbmsDocument.attachments
        )
        sendMessageResponseToPayloadTopic(signedEbmsDocument, validationResult.receiverEmailAddress)
        if (processedMessage.ackRequested) {
            storeMessagePendingAck(signedEbmsDocument, validationResult.receiverEmailAddress)
        }
        log.info(processedMessage.marker(), "Payload message response returned successfully")
    }

    // This resend function is to be used from Resend processing
    suspend fun resendMessage(message: MessagePendingAck) {
        resendMessageToPayloadTopic(message)
        log.info("Payload message resent successfully")
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

    private suspend fun sendMessageResponseToPayloadTopic(signedEbmsDocument: EbmsDocument, receiverEmailAddress: List<EmailAddress>) {
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
                value = signedEbmsDocument.document.toByteArray(),
                headers = buildPayloadHeaders(signedEbmsDocument.messageHeader(), receiverEmailAddress)
            )
        }
    }

    private fun buildPayloadHeaders(
        messageHeader: MessageHeader,
        receiverEmailAddressAsObjects: List<EmailAddress>? = null,
        receiverEmailAddressAsStrings: List<String>? = null
    ): MutableList<Header> {
        val headers = mutableListOf<Header>()
        if (receiverEmailAddressAsObjects != null) {
            headers.addAll(receiverEmailAddressAsObjects.toKafkaHeaders())
        }
        if (receiverEmailAddressAsStrings != null) {
            headers.addAll(toAddressKafkaHeader(receiverEmailAddressAsStrings))
        }
        headers.addAll(messageHeader.toKafkaHeaders())
        return headers
    }

    // The given record/message will be like the one produced by sendMessageResponseToPayloadTopic, just use its key, value and headers
    private suspend fun resendMessageToPayloadTopic(message: MessagePendingAck) {
        eventRegistrationService.runWithEvent(
            successEvent = EventType.MESSAGE_PLACED_IN_QUEUE,
            failEvent = EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
            requestId = message.requestId.parseOrGenerateUuid(),
            messageId = message.messageId,
            eventData = Json.encodeToString(
                mapOf(EventDataType.QUEUE_NAME.value to config().kafkaPayloadProducer.topic)
            )
        ) {
            ebmsPayloadProducer.publishMessage(
                key = message.requestId,
                value = message.messageContent,
                headers = buildPayloadHeaders(message.messageHeader, null, message.emailAddressList)
            )
        }
    }

    private fun storeMessagePendingAck(ebmsDocument: EbmsDocument, receiverEmailAddress: List<EmailAddress>) {
        messagePendingAckRepository.storeMessagePendingAck(Uuid.parse(ebmsDocument.requestId), ebmsDocument.messageHeader(), ebmsDocument.document.toByteArray(), receiverEmailAddress)
    }
}
