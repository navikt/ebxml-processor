package no.nav.emottak.payload.util

import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.log
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import kotlin.uuid.ExperimentalUuidApi

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        payloadRequest: PayloadRequest,
        eventData: String = "{}"
    )
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        payloadRequest: PayloadRequest,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${payloadRequest.requestId}")

        try {
            val event = Event(
                eventType = eventType,
                requestId = payloadRequest.requestId.parseOrGenerateUuid(),
                contentId = payloadRequest.payload.contentId,
                messageId = payloadRequest.messageId,
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
        payloadRequest: PayloadRequest,
        eventData: String
    ) {
        log.debug("Registering event $eventType for validationRequest: $payloadRequest and eventData: $eventData")
    }
}
