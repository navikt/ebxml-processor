package no.nav.emottak.ebms.util

import no.nav.emottak.ebms.log
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        ebMSDocument: EbMSDocument,
        eventData: String = ""
    )
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        ebMSDocument: EbMSDocument,
        eventData: String
    ) {
        log.debug("Event reg. test: Registering event for requestId: ${ebMSDocument.requestId}")

        try {
            val requestId = ebMSDocument.requestId.parseOrGenerateUuid()

            log.debug("Event reg. test: RequestId: $requestId")

            val event = Event(
                eventType = eventType,
                requestId = requestId,
                contentId = "",
                messageId = ebMSDocument.transform().messageId,
                eventData = eventData
            )

            log.debug("Event reg. test: Publishing event: $event")
            eventLoggingService.logEvent(event)
            log.debug("Event reg. test: Event published successfully")
        } catch (e: Exception) {
            log.error("Event reg. test: Error while registering event: ${e.message}", e)
        }
    }
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        ebMSDocument: EbMSDocument,
        eventData: String
    ) {
        log.debug("Event reg. test: Registering event $eventType for ebMSDocument: $ebMSDocument and eventData: $eventData")
    }
}
