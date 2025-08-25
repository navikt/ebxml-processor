package no.nav.emottak.ebms.async.util

import kotlin.uuid.Uuid
import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.kafka.model.EventType

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
        requestId: Uuid,
        contentId: String,
        messageId: String,
        eventData: String
    ) {
        log.debug("Registering event $eventType for requestId: $requestId and eventData: $eventData")
    }

    override suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: Uuid,
        contentId: String,
        messageId: String,
        eventData: String,
        function: suspend () -> T
    ): T {
        log.debug("Registering events $successEvent and $failEvent for requestId: $requestId and eventData: $eventData")
        return function.invoke()
    }
}
