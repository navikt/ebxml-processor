package no.nav.emottak.ebms.async.util

import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import kotlin.uuid.ExperimentalUuidApi

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        validationRequest: ValidationRequest,
        requestId: String,
        eventData: String = "{}"
    )
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        validationRequest: ValidationRequest,
        requestId: String,
        eventData: String
    ) {
        log.debug("Registering event for requestId: $requestId")

        try {
            val event = Event(
                eventType = eventType,
                requestId = requestId.parseOrGenerateUuid(),
                contentId = "",
                messageId = validationRequest.messageId,
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
        validationRequest: ValidationRequest,
        requestId: String,
        eventData: String
    ) {
        log.debug("Registering event $eventType for validationRequest: $validationRequest and eventData: $eventData")
    }
}
