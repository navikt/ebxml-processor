package no.nav.emottak.ebms.util

import no.nav.emottak.ebms.log
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        ebMSDocument: EbMSDocument,
        eventData: String = "{}"
    )

    suspend fun registerEventMessageDetails(ebMSDocument: EbMSDocument)

    companion object {
        fun serializePartyId(partyIDs: List<PartyId>): String {
            val partyId = partyIDs.firstOrNull { it.type == "orgnummer" }
                ?: partyIDs.firstOrNull { it.type == "HER" }
                ?: partyIDs.firstOrNull { it.type == "ENH" }
                ?: partyIDs.first()

            return "${partyId.type}:${partyId.value}"
        }
    }
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        ebMSDocument: EbMSDocument,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${ebMSDocument.requestId}")

        try {
            val requestId = ebMSDocument.requestId.parseOrGenerateUuid()

            val event = Event(
                eventType = eventType,
                requestId = requestId,
                contentId = "",
                messageId = ebMSDocument.transform().messageId,
                eventData = eventData
            )
            log.debug("Publishing event: $event")

            eventLoggingService.logEvent(event)
            log.debug("Event published successfully")
        } catch (e: Exception) {
            log.error("Error while registering event: ${e.message}", e)
        }
    }

    override suspend fun registerEventMessageDetails(ebMSDocument: EbMSDocument) {
        log.debug("Registering message with requestId: ${ebMSDocument.requestId}")

        try {
            val ebmsMessage = ebMSDocument.transform()
            val requestId = ebmsMessage.requestId.parseOrGenerateUuid()

            val ebmsMessageDetails = EbmsMessageDetails(
                requestId = requestId,
                cpaId = ebmsMessage.cpaId,
                conversationId = ebmsMessage.conversationId,
                messageId = ebmsMessage.messageId,
                refToMessageId = ebmsMessage.refToMessageId,
                fromPartyId = EventRegistrationService.serializePartyId(ebmsMessage.addressing.from.partyId),
                fromRole = ebmsMessage.addressing.from.role,
                toPartyId = EventRegistrationService.serializePartyId(ebmsMessage.addressing.to.partyId),
                toRole = ebmsMessage.addressing.to.role,
                service = ebmsMessage.addressing.service,
                action = ebmsMessage.addressing.action,
                refParam = null,
                sender = null,
                sentAt = ebmsMessage.sentAt
            )
            log.debug("Publishing message details: $ebmsMessageDetails")

            eventLoggingService.logMessageDetails(ebmsMessageDetails)
            log.debug("Message details published successfully")
        } catch (e: Exception) {
            log.error("Error while registering message details: ${e.message}", e)
        }
    }
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        ebMSDocument: EbMSDocument,
        eventData: String
    ) {
        log.debug("Registering event $eventType for ebMSDocument: $ebMSDocument and eventData: $eventData")
    }

    override suspend fun registerEventMessageDetails(ebMSDocument: EbMSDocument) {
        log.debug("Registering message details for ebMSDocument: $ebMSDocument")
    }
}
