package no.nav.emottak.cpa.util

import no.nav.emottak.cpa.log
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        validationRequest: ValidationRequest,
        eventData: String = "{}"
    )
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        validationRequest: ValidationRequest,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${validationRequest.requestId}")

        try {
            val requestId = validationRequest.requestId.parseOrGenerateUuid()

            val event = Event(
                eventType = eventType,
                requestId = requestId,
                contentId = "",
                messageId = validationRequest.messageId,
                eventData = eventData
            )
            log.debug("Publishing event: $event")

            eventLoggingService.logEvent(event)
            log.debug("Event published successfully")
        } catch (e: Exception) {
            log.error("Error while registering event: ${e.message}", e)
        }
    }
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        validationRequest: ValidationRequest,
        eventData: String
    ) {
        log.debug("Registering event $eventType for validationRequest: $validationRequest and eventData: $eventData")
    }
}
