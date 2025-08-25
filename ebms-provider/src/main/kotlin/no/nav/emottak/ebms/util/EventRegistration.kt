package no.nav.emottak.ebms.util

import no.nav.emottak.ebms.log
import no.nav.emottak.message.model.EbMSDocument
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
        ebMSDocument: EbMSDocument,
        eventData: String = "{}"
    )

    suspend fun registerEventMessageDetails(ebMSDocument: EbMSDocument)

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
        try {
            registerEventMessageDetails(ebMSDocument.transform())
        } catch (e: Exception) {
            log.error("Error while registering message details: ${e.message}", e)
        }
    }

    override suspend fun registerEventMessageDetails(ebMSMessage: EbmsMessage) {
        log.debug("Registering message with requestId: ${ebMSMessage.requestId}")

        try {
            val requestId = ebMSMessage.requestId.parseOrGenerateUuid()

            val ebmsMessageDetail = EbmsMessageDetail(
                requestId = requestId,
                cpaId = ebMSMessage.cpaId,
                conversationId = ebMSMessage.conversationId,
                messageId = ebMSMessage.messageId,
                refToMessageId = ebMSMessage.refToMessageId,
                fromPartyId = EventRegistrationService.serializePartyId(ebMSMessage.addressing.from.partyId),
                fromRole = ebMSMessage.addressing.from.role,
                toPartyId = EventRegistrationService.serializePartyId(ebMSMessage.addressing.to.partyId),
                toRole = ebMSMessage.addressing.to.role,
                service = ebMSMessage.addressing.service,
                action = ebMSMessage.addressing.action,
                sentAt = ebMSMessage.sentAt
            )
            log.debug("Publishing message details: $ebmsMessageDetail")

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
        ebMSDocument: EbMSDocument,
        eventData: String
    ) {
        log.debug("Registering event $eventType for ebMSDocument: $ebMSDocument and eventData: $eventData")
    }

    override suspend fun registerEventMessageDetails(ebMSDocument: EbMSDocument) {
        log.debug("Registering message details for ebMSDocument: $ebMSDocument")
    }

    override suspend fun registerEventMessageDetails(ebMSMessage: EbmsMessage) {
        log.debug("Registering message details for ebMSDocument: $ebMSMessage")
    }
}
