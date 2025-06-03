package no.nav.emottak.ebms.async.util

import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String = "{}"
    )

    suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String = "{}"
    )

    @OptIn(ExperimentalUuidApi::class)
    suspend fun registerEvent(
        eventType: EventType,
        requestId: Uuid,
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

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${asyncPayload.referenceId}")

        try {
            val event = Event(
                eventType = eventType,
                requestId = asyncPayload.referenceId.parseOrGenerateUuid(),
                contentId = asyncPayload.contentId,
                messageId = "",
                eventData = eventData
            )
            log.debug("Registering event: {}", event)

            eventLoggingService.logEvent(event)
            log.debug("Event is registered successfully")
        } catch (e: Exception) {
            log.error("Error while registering event: ${e.message}", e)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        requestId: Uuid,
        eventData: String
    ) {
        log.debug("Registering event for requestId: $requestId")

        try {
            val event = Event(
                eventType = eventType,
                requestId = requestId,
                contentId = "",
                messageId = "",
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

    override suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String
    ) {
        log.debug("Registering event $eventType for asyncPayload: $asyncPayload and eventData: $eventData")
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        requestId: Uuid,
        eventData: String
    ) {
        log.debug("Registering event $eventType for requestId: $requestId and eventData: $eventData")
    }
}
