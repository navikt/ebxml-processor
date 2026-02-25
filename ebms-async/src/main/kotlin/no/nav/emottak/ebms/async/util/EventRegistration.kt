package no.nav.emottak.ebms.async.util

import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.log
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import kotlin.uuid.Uuid

interface EventRegistrationService {

    suspend fun registerEventMessageDetails(ebmsMessage: EbmsMessage)

    suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String = "{}",
        conversationId: String? = null
    )

    suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String = "{}",
        conversationId: String? = null
    )

    suspend fun registerEvent(
        eventType: EventType,
        requestId: Uuid,
        contentId: String = "",
        messageId: String = "",
        eventData: String = "{}",
        conversationId: String? = null
    )

    suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: Uuid,
        contentId: String = "",
        messageId: String = "",
        eventData: String = "{}",
        conversationId: String? = null,
        function: suspend () -> T
    ): T
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {

    override suspend fun registerEventMessageDetails(ebmsMessage: EbmsMessage) {
        log.debug(ebmsMessage.marker(), "Registering message with requestId: ${ebmsMessage.requestId}")

        try {
            val ebmsMessageDetail = EbmsMessageDetail(
                requestId = ebmsMessage.requestId.parseOrGenerateUuid(),
                cpaId = ebmsMessage.cpaId,
                conversationId = ebmsMessage.conversationId,
                messageId = ebmsMessage.messageId,
                refToMessageId = ebmsMessage.refToMessageId,
                fromPartyId = no.nav.emottak.ebms.util.EventRegistrationService.serializePartyId(ebmsMessage.addressing.from.partyId),
                fromRole = ebmsMessage.addressing.from.role,
                toPartyId = no.nav.emottak.ebms.util.EventRegistrationService.serializePartyId(ebmsMessage.addressing.to.partyId),
                toRole = ebmsMessage.addressing.to.role,
                service = ebmsMessage.addressing.service,
                action = ebmsMessage.addressing.action,
                sentAt = ebmsMessage.sentAt
            )
            log.debug(ebmsMessage.marker(), "Publishing message details: {}", ebmsMessageDetail)

            eventLoggingService.logMessageDetails(ebmsMessageDetail)
            log.debug(ebmsMessage.marker(), "Message details published successfully")
        } catch (e: Exception) {
            log.error(ebmsMessage.marker(), "Error while registering message details: ${e.message}", e)
        }
    }

    override suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String,
        conversationId: String?
    ) {
        registerEvent(
            Event(
                eventType = eventType,
                requestId = payloadMessage.requestId.parseOrGenerateUuid(),
                contentId = payloadMessage.payload.contentId,
                messageId = payloadMessage.messageId,
                eventData = eventData,
                conversationId = conversationId
            )
        )
    }

    override suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String,
        conversationId: String?
    ) {
        registerEvent(
            Event(
                eventType = eventType,
                requestId = asyncPayload.referenceId,
                contentId = asyncPayload.contentId,
                messageId = "",
                eventData = eventData,
                conversationId = conversationId
            )
        )
    }

    override suspend fun registerEvent(
        eventType: EventType,
        requestId: Uuid,
        contentId: String,
        messageId: String,
        eventData: String,
        conversationId: String?
    ) {
        registerEvent(
            Event(
                eventType = eventType,
                requestId = requestId,
                contentId = contentId,
                messageId = messageId,
                eventData = eventData,
                conversationId = conversationId
            )
        )
    }

    override suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: Uuid,
        contentId: String,
        messageId: String,
        eventData: String,
        conversationId: String?,
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
                eventData = eventData,
                conversationId = conversationId
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
                eventData = updatedEventData,
                conversationId = conversationId
            )
        }.getOrThrow()
    }

    private suspend fun registerEvent(event: Event) {
        try {
            log.debug(event.marker(), "Registering event: {}", event)
            eventLoggingService.logEvent(event)
            log.debug(event.marker(), "Event is registered successfully")
        } catch (e: Exception) {
            log.error(event.marker(), "Error while registering event: ${e.message}", e)
        }
    }
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEventMessageDetails(ebmsMessage: EbmsMessage) {
        log.debug("Registering message details for ebmsDocument: {}", ebmsMessage)
    }

    override suspend fun registerEvent(
        eventType: EventType,
        payloadMessage: PayloadMessage,
        eventData: String,
        conversationId: String?
    ) {
        log.debug(
            "Registering event {} for payloadMessage: {}, conversationId: {} and eventData: {}",
            eventType,
            payloadMessage,
            conversationId,
            eventData
        )
    }

    override suspend fun registerEvent(
        eventType: EventType,
        asyncPayload: AsyncPayload,
        eventData: String,
        conversationId: String?
    ) {
        log.debug(
            "Registering event {} for asyncPayload: {}, conversationId: {} and eventData: {}",
            eventType,
            asyncPayload,
            conversationId,
            eventData
        )
    }

    override suspend fun registerEvent(
        eventType: EventType,
        requestId: Uuid,
        contentId: String,
        messageId: String,
        eventData: String,
        conversationId: String?
    ) {
        log.debug(
            "Registering event {} for requestId: {}, conversationId: {} and eventData: {}",
            eventType,
            requestId,
            conversationId,
            eventData
        )
    }

    override suspend fun <T> runWithEvent(
        successEvent: EventType,
        failEvent: EventType,
        requestId: Uuid,
        contentId: String,
        messageId: String,
        eventData: String,
        conversationId: String?,
        function: suspend () -> T
    ): T {
        log.debug(
            "Registering events {} and {} for requestId: {}, conversationId: {} and eventData: {}",
            successEvent,
            failEvent,
            requestId,
            conversationId,
            eventData
        )
        return function.invoke()
    }
}
