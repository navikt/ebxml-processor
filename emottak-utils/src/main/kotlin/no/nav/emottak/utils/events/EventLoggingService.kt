package no.nav.emottak.utils.events

import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventMessageDetails
import no.nav.emottak.utils.events.model.EventType
import no.nav.emottak.utils.kafka.KafkaPublisherClient
import no.nav.emottak.utils.toJsonString
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class EventLoggingService(private val kafkaPublisher: KafkaPublisherClient) {

    suspend fun logEvent(event: Event) {
        kafkaPublisher.send(kafkaPublisher.topic, event.toByteArray())
    }

    suspend fun logEventMessageDetails(eventMessageDetails: EventMessageDetails) {
        kafkaPublisher.send(kafkaPublisher.topic, eventMessageDetails.toByteArray())
    }

    suspend fun logEventMessageDetails(
        requestId: Uuid,
        cpaId: String,
        conversationId: String,
        messageId: String,
        refToMessageId: String? = null,
        fromPartyId: String,
        fromRole: String? = null,
        toPartyId: String,
        toRole: String? = null,
        service: String,
        action: String,
        sentAt: Instant? = null
    ) {
        logEventMessageDetails(
            EventMessageDetails(
                requestId = requestId,
                cpaId = cpaId,
                conversationId = conversationId,
                messageId = messageId,
                refToMessageId = refToMessageId,
                fromPartyId = fromPartyId,
                fromRole = fromRole,
                toPartyId = toPartyId,
                toRole = toRole,
                service = service,
                action = action,
                sentAt = sentAt
            )
        )
    }

    suspend fun logEventOK(eventType: EventType, requestId: Uuid, messageId: String, contentId: String? = null, eventData: String? = null) {
        logEvent(
            Event(
                eventType = eventType,
                requestId = requestId,
                contentId = contentId,
                messageId = messageId,
                eventData = eventData
            )
        )
    }

    suspend fun logEventException(eventType: EventType, requestId: Uuid, messageId: String, ex: Exception, contentId: String? = null) {
        logEvent(
            Event(
                eventType = eventType,
                requestId = requestId,
                contentId = contentId,
                messageId = messageId,
                eventData = ex.toJsonString()
            )
        )
    }
}
