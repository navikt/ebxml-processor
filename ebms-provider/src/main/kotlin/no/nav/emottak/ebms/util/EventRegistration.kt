package no.nav.emottak.ebms.util

import no.nav.emottak.ebms.log
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        ebmsDocument: EbmsDocument,
        eventData: String = "{}"
    )

    suspend fun registerEventMessageDetails(ebmsDocument: EbmsDocument)

    suspend fun registerEventMessageDetails(ebmsMessage: EbmsMessage)

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
        ebmsDocument: EbmsDocument,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${ebmsDocument.requestId}")

        try {
            val requestId = ebmsDocument.requestId.parseOrGenerateUuid()
            val ebmsMessage = ebmsDocument.transform()
            val event = Event(
                eventType = eventType,
                requestId = requestId,
                contentId = "",
                messageId = ebmsMessage.messageId,
                eventData = eventData,
                conversationId = ebmsMessage.conversationId
            )
            log.debug("Publishing event: {}", event)

            eventLoggingService.logEvent(event)
            log.debug("Event published successfully")
        } catch (e: Exception) {
            log.error("Error while registering event: ${e.message}", e)
        }
    }

    override suspend fun registerEventMessageDetails(ebmsDocument: EbmsDocument) {
        registerEventMessageDetails(ebmsDocument.transform())
    }

    override suspend fun registerEventMessageDetails(ebmsMessage: EbmsMessage) {
        log.debug("Registering message with requestId: ${ebmsMessage.requestId}")

        try {
            val requestId = ebmsMessage.requestId.parseOrGenerateUuid()

            val ebmsMessageDetail = EbmsMessageDetail(
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
                sentAt = ebmsMessage.sentAt
            )
            log.debug("Publishing message details: {}", ebmsMessageDetail)

            eventLoggingService.logMessageDetails(ebmsMessageDetail)
            log.debug("Message details published successfully")
        } catch (e: Exception) {
            log.error("Error while registering message details: ${e.message}", e)
        }
    }
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        ebmsDocument: EbmsDocument,
        eventData: String
    ) {
        log.debug("Registering event {} for ebmsDocument: {} and eventData: {}", eventType, ebmsDocument, eventData)
    }

    override suspend fun registerEventMessageDetails(ebmsDocument: EbmsDocument) {
        log.debug("Registering message details for ebmsDocument: {}", ebmsDocument)
    }

    override suspend fun registerEventMessageDetails(ebmsMessage: EbmsMessage) {
        log.debug("Registering message details for ebmsDocument: {}", ebmsMessage)
    }
}
