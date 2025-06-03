package no.nav.emottak.ebms.async.util

import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import kotlin.uuid.ExperimentalUuidApi

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String = "{}"
    )
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${payloadMessage.requestId}")

        try {
            val event = Event(
                eventType = eventType,
                requestId = payloadMessage.requestId.parseOrGenerateUuid(),
                contentId = payloadMessage.payload.contentId,
                messageId = payloadMessage.messageId,
                eventData = eventData
            )
            log.debug("Registering event: {}", event)

            eventLoggingService.logEvent(event)
            log.debug("Event is registered successfully")
        } catch (e: Exception) {
            log.error("Error while registering event: ${e.message}", e)
        }
    }
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String
    ) {
        log.debug("Registering event $eventType for payloadMessage: $payloadMessage and eventData: $eventData")
    }
}
