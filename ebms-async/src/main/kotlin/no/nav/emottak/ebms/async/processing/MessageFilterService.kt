package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.incrementFirstFailure
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.REASON_FORCED_RETRY
import no.nav.emottak.ebms.async.kafka.consumer.RETRY_REASON
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.persistence.repository.MessageReceivedRepository
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.DocumentType
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.documentType
import no.nav.emottak.message.xml.createDocument
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.w3c.dom.Document
import kotlin.uuid.Uuid

// Vi ønsker ikke å retrye meldinger som ikke kan parses som EBXML mer enn 1 gang.
// De vil da gi alert og kunne rekjøres manuelt fra feilkø, dersom årsaken er kodefeil i parsingen.
const val MAX_RETRIES_FOR_INVALID_EBXML = 1

open class MessageFilterService(
    val payloadMessageService: PayloadMessageService,
    val signalMessageService: SignalMessageService,
    val smtpTransportClient: SmtpTransportClient,
    val eventRegistrationService: EventRegistrationService,
    val failedMessageKafkaHandler: FailedMessageKafkaHandler,
    val messageReceivedRepository: MessageReceivedRepository
) {

    open suspend fun filterMessage(record: ReceiverRecord<String, ByteArray>) {
        val ebmsMessage = try {
            createEbmsDocument(
                requestId = record.key(),
                document = record.value().createDocument()
            )
        } catch (e: Exception) {
            log.error("Failed to create ebmsDocument", e)
            if (record.retryCount() == 0) {
                failedMessageKafkaHandler.meterRegistry.incrementFirstFailure("incoming", "unknown_service_unparseable_EBXML", "unknown_action_unparseable_EBXML")
            }
            if (record.retryCount() < MAX_RETRIES_FOR_INVALID_EBXML) {
                failedMessageKafkaHandler.sendToRetryQueueIncoming(record, e.javaClass.simpleName + ": " + e.localizedMessage)
            } else {
                log.error("Failed to create ebmsDocument and max number of retries performed, giving up message! Offset in retry topic: ${record.offset}", e)
            }
            return
        }
        eventRegistrationService.registerEvent(
            eventType = EventType.MESSAGE_READ_FROM_QUEUE,
            requestId = ebmsMessage.requestId.parseOrGenerateUuid(),
            messageId = ebmsMessage.messageId,
            eventData = Json.encodeToString(
                mapOf(EventDataType.QUEUE_NAME.value to record.topic())
            ),
            conversationId = ebmsMessage.conversationId
        )
        val forceSkipDuplicateCheck = record.headers().lastHeader(RETRY_REASON)?.value()?.let {
            String(it) == REASON_FORCED_RETRY
        } ?: false
        when (ebmsMessage) {
            is PayloadMessage -> payloadMessageService.process(record, ebmsMessage, forceSkipDuplicateCheck)
            is Acknowledgment -> signalMessageService.processSignal(record.key(), ebmsMessage)
            is MessageError -> signalMessageService.processSignal(record.key(), ebmsMessage)
        }
    }

    private suspend fun createEbmsDocument(
        requestId: String,
        document: Document
    ): EbmsMessage {
        val message = EbmsDocument(
            requestId = requestId,
            document = document,
            attachments = if (document.documentType() == DocumentType.PAYLOAD) {
                retrievePayloads(requestId.parseOrGenerateUuid())
            } else {
                emptyList()
            }
        ).transform()
        // Prodfeil 26/8-2026: Mottok MessageError uten refToMessageId.
        // Dette er ikke lov, men vi er istand til å finne original melding via conversationId og kan da akseptere slike
        if (message is MessageError && message.refToMessageId == null) {
            val firstByConversationId = messageReceivedRepository.getFirstByConversationId(message.conversationId)
            return message.copy(refToMessageId = firstByConversationId?.messageId)
        }
        return message
    }

    private suspend fun retrievePayloads(reference: Uuid): List<Payload> {
        return smtpTransportClient.getPayload(reference)
            .map {
                eventRegistrationService.runWithEvent(
                    successEvent = EventType.PAYLOAD_RECEIVED_VIA_HTTP,
                    failEvent = EventType.ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP,
                    requestId = reference,
                    contentId = it.contentId
                    // conversationId ikke tilgjengelig
                ) {
                    Payload(
                        bytes = it.content,
                        contentId = it.contentId,
                        contentType = it.contentType
                    )
                }
            }
    }
}
