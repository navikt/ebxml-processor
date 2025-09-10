package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.DokumentType
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.dokumentType
import no.nav.emottak.message.xml.createDocument
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.w3c.dom.Document
import kotlin.uuid.Uuid

class MessageFilterService(
    val payloadMessageService: PayloadMessageService,
    val signalMessageService: SignalMessageService,
    val smtpTransportClient: SmtpTransportClient,
    val eventRegistrationService: EventRegistrationService
) {

    suspend fun filterMessage(record: ReceiverRecord<String, ByteArray>) {
        val ebmsMessage = createEbmsDocument(
            requestId = record.key(),
            document = record.value().createDocument()
        )
        eventRegistrationService.registerEvent(
            eventType = EventType.MESSAGE_READ_FROM_QUEUE,
            requestId = ebmsMessage.requestId.parseOrGenerateUuid(),
            messageId = ebmsMessage.messageId,
            eventData = Json.encodeToString(
                mapOf(EventDataType.QUEUE_NAME.value to record.topic())
            )
        )
        when (ebmsMessage) {
            is PayloadMessage -> payloadMessageService.process(record, ebmsMessage)
            is Acknowledgment -> signalMessageService.processSignal(record.key(), ebmsMessage)
            is MessageError -> signalMessageService.processSignal(record.key(), ebmsMessage)
        }
    }

    private suspend fun createEbmsDocument(
        requestId: String,
        document: Document
    ): EbmsMessage = EbMSDocument(
        requestId = requestId,
        dokument = document,
        attachments = if (document.dokumentType() == DokumentType.PAYLOAD) {
            retrievePayloads(requestId.parseOrGenerateUuid())
        } else {
            emptyList()
        }
    ).transform()

    private suspend fun retrievePayloads(reference: Uuid): List<Payload> {
        return smtpTransportClient.getPayload(reference)
            .map {
                eventRegistrationService.runWithEvent(
                    successEvent = EventType.PAYLOAD_RECEIVED_VIA_HTTP,
                    failEvent = EventType.ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP,
                    requestId = reference,
                    contentId = it.contentId
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
