package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.SmtpTransportClient
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
import no.nav.emottak.utils.common.model.SendInResponse
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.w3c.dom.Document
import kotlin.uuid.Uuid

open class MessageFilterService(
    val payloadMessageService: PayloadMessageService,
    val signalMessageService: SignalMessageService,
    val smtpTransportClient: SmtpTransportClient,
    val eventRegistrationService: EventRegistrationService
) {

    open suspend fun filterMessage(record: ReceiverRecord<String, ByteArray>) {
        val jsonResponse = runCatching {
            Json.decodeFromString<SendInResponse>(record.value().decodeToString())
        }.getOrNull()

        val ebmsMessage: EbmsMessage = if (jsonResponse != null) {
            val cpaId = record.headers().lastHeader("cpaId")?.let { String(it.value()) } ?: ""
            val refToMessageId = record.headers().lastHeader("refToMessageId")?.let { String(it.value()) }
            PayloadMessage(
                requestId = jsonResponse.requestId,
                messageId = jsonResponse.messageId,
                conversationId = jsonResponse.conversationId,
                cpaId = cpaId,
                addressing = jsonResponse.addressing,
                payload = Payload(jsonResponse.payload, ContentType.Application.Xml.toString()),
                refToMessageId = refToMessageId,
                duplicateElimination = false,
                ackRequested = true
            )
        } else {
            createEbmsDocument(
                requestId = record.key(),
                document = record.value().createDocument()
            )
        }

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
    ): EbmsMessage = EbmsDocument(
        requestId = requestId,
        document = document,
        attachments = if (document.documentType() == DocumentType.PAYLOAD) {
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
