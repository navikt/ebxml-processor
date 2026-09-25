package no.nav.emottak.payload.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.log
import no.nav.emottak.util.mapCertificateDetails
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import java.security.cert.X509Certificate
import kotlin.uuid.ExperimentalUuidApi

interface EventRegistrationService {
    suspend fun registerEvent(
        eventType: EventType,
        payloadRequest: PayloadRequest,
        eventData: String = "{}"
    )

    suspend fun registerPayloadEncrypted(payloadRequest: PayloadRequest, certificate: X509Certificate)
    suspend fun registerPayloadDecrypted(payloadRequest: PayloadRequest)
    suspend fun registerPayloadCompressed(payloadRequest: PayloadRequest)
    suspend fun registerPayloadDecompressed(payloadRequest: PayloadRequest)
}

class EventRegistrationServiceImpl(
    private val eventLoggingService: EventLoggingService
) : EventRegistrationService {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun registerEvent(
        eventType: EventType,
        payloadRequest: PayloadRequest,
        eventData: String
    ) {
        log.debug("Registering event for requestId: ${payloadRequest.requestId}")

        try {
            val event = Event(
                eventType = eventType,
                requestId = payloadRequest.requestId.parseOrGenerateUuid(),
                contentId = payloadRequest.payload.contentId,
                messageId = payloadRequest.messageId,
                eventData = eventData,
                conversationId = payloadRequest.conversationId
            )
            log.debug("Registering event: {}", event)

            eventLoggingService.logEvent(event)
            log.debug("Event is registered successfully")
        } catch (e: Exception) {
            log.error("Error while registering event: ${e.message}", e)
        }
    }

    override suspend fun registerPayloadEncrypted(
        payloadRequest: PayloadRequest,
        certificate: X509Certificate
    ) = registerEvent(
        EventType.MESSAGE_ENCRYPTED,
        payloadRequest,
        Json.encodeToString(certificate.mapCertificateDetails())
    )

    override suspend fun registerPayloadDecrypted(payloadRequest: PayloadRequest) = registerEvent(
        EventType.MESSAGE_DECRYPTED,
        payloadRequest
    )

    override suspend fun registerPayloadCompressed(payloadRequest: PayloadRequest) = registerEvent(
        EventType.MESSAGE_COMPRESSED,
        payloadRequest
    )

    override suspend fun registerPayloadDecompressed(payloadRequest: PayloadRequest) = registerEvent(
        EventType.MESSAGE_DECOMPRESSED,
        payloadRequest
    )
}

class EventRegistrationServiceFake : EventRegistrationService {
    override suspend fun registerEvent(
        eventType: EventType,
        payloadRequest: PayloadRequest,
        eventData: String
    ) = log.debug(
        "Registering event {} for validationRequest: {} and eventData: {}",
        eventType,
        payloadRequest,
        eventData
    )

    override suspend fun registerPayloadEncrypted(
        payloadRequest: PayloadRequest,
        certificate: X509Certificate
    ) = log.debug(
        "Registering event MESSAGE_ENCRYPTED for validationRequest: {} and certificate: {}",
        payloadRequest,
        certificate
    )

    override suspend fun registerPayloadDecrypted(payloadRequest: PayloadRequest) =
        log.debug(
            "Registering event MESSAGE_DECRYPTED for validationRequest: {}",
            payloadRequest
        )

    override suspend fun registerPayloadCompressed(payloadRequest: PayloadRequest) =
        log.debug(
            "Registering event MESSAGE_COMPRESSED for validationRequest: {}",
            payloadRequest
        )

    override suspend fun registerPayloadDecompressed(payloadRequest: PayloadRequest) =
        log.debug(
            "Registering event MESSAGE_DECOMPRESSED for validationRequest: {}",
            payloadRequest
        )
}
