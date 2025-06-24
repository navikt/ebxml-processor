package no.nav.emottak.ebms.async.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService

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

    suspend fun registerEvent(
        eventType: EventType,
        requestId: String,
        contentId: String = "",
        messageId: String = "",
        eventData: String = "{}"
    )

    suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: String,
        contentId: String = "",
        messageId: String = "",
        eventData: String = "{}",
        function: suspend () -> T
    ): T
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {

    override suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String
    ) {
        registerEvent(
            Event(
                eventType = eventType,
                requestId = payloadMessage.requestId.parseOrGenerateUuid(),
                contentId = payloadMessage.payload.contentId,
                messageId = payloadMessage.messageId,
                eventData = eventData
            )
        )
    }

    override suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String
    ) {
        registerEvent(
            Event(
                eventType = eventType,
                requestId = asyncPayload.referenceId.parseOrGenerateUuid(),
                contentId = asyncPayload.contentId,
                messageId = "",
                eventData = eventData
            )
        )
    }

    override suspend fun registerEvent(
        eventType: EventType,
        requestId: String,
        contentId: String,
        messageId: String,
        eventData: String
    ) {
        registerEvent(
            Event(
                eventType = eventType,
                requestId = requestId.parseOrGenerateUuid(),
                contentId = contentId,
                messageId = messageId,
                eventData = eventData
            )
        )
    }

    override suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: String,
        contentId: String,
        messageId: String,
        eventData: String,
        function: suspend () -> T
    ): T {
        return runCatching {
            function.invoke()
        }.onSuccess {
            this.registerEvent(
                successEvent,
                requestId = requestId,
                contentId = contentId,
                messageId = messageId,
                eventData = eventData
            )
        }.onFailure {
            val updatedEventData = Json.encodeToString(
                Json.decodeFromString<Map<String, String>>(eventData)
                    .plus(EventDataType.ERROR_MESSAGE.value to it.message)
            )
            this.registerEvent(
                failEvent,
                requestId = requestId,
                contentId = contentId,
                messageId = messageId,
                eventData = updatedEventData
            )
        }.getOrThrow()
    }

    private suspend fun registerEvent(event: Event) {
        try {
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

    override suspend fun registerEvent(
        eventType: EventType,
        requestId: String,
        contentId: String,
        messageId: String,
        eventData: String
    ) {
        log.debug("Registering event $eventType for requestId: $requestId and eventData: $eventData")
    }

    override suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: String,
        contentId: String,
        messageId: String,
        eventData: String,
        function: suspend () -> T
    ): T {
        log.debug("Registering events $successEvent and $failEvent for requestId: $requestId and eventData: $eventData")
        return function.invoke()
    }
}
