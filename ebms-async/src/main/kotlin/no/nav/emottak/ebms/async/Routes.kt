package no.nav.emottak.ebms.async

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.emottak.ebms.StatusResponse
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.getRecord
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.processing.MessageFilterService
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.serialization.toEventDataJson
import kotlin.uuid.Uuid

private const val REFERENCE_ID = "referenceId"
private const val RETRY_LIMIT = "retryLimit"
private const val KAFKA_OFFSET = "offset"
private const val MESSAGE_ID = "messageId"

fun Route.getPayloads(
    payloadRepository: PayloadRepository,
    eventRegistrationService: EventRegistrationService
): Route = get("/api/payloads/{$REFERENCE_ID}") {
    var referenceIdParameter: String? = null
    val referenceId: Uuid?
    // Validation
    try {
        referenceIdParameter = call.parameters[REFERENCE_ID]
        referenceId = Uuid.parse(referenceIdParameter!!)
    } catch (iae: IllegalArgumentException) {
        log.error("Invalid reference ID $referenceIdParameter has been sent", iae)
        call.respond(
            HttpStatusCode.BadRequest,
            iae.getErrorMessage()
        )
        return@get
    } catch (ex: Exception) {
        log.error("Exception occurred while validation of async payload request")
        call.respond(
            HttpStatusCode.BadRequest,
            ex.getErrorMessage()
        )
        return@get
    }

    // Sending response
    try {
        val listOfPayloads = payloadRepository.getByReferenceId(referenceId)

        if (listOfPayloads.isEmpty()) {
            call.respond(HttpStatusCode.NotFound, "Payload not found for reference ID $referenceId")

            eventRegistrationService.registerEvent(
                EventType.ERROR_WHILE_READING_PAYLOAD_FROM_DATABASE,
                requestId = referenceId,
                eventData = Exception("Payload not found for reference ID $referenceId").toEventDataJson()
                // conversationId ikke tilgjengelig
            )
        } else {
            call.respond(HttpStatusCode.OK, listOfPayloads)

            listOfPayloads.forEach {
                eventRegistrationService.registerEvent(
                    EventType.PAYLOAD_READ_FROM_DATABASE,
                    it
                    // conversationId ikke tilgjengelig
                )
            }
        }
    } catch (ex: Exception) {
        log.error("Exception occurred while retrieving Payload: ${ex.localizedMessage} (${ex::class.qualifiedName})")
        call.respond(
            HttpStatusCode.InternalServerError,
            ex.getErrorMessage()
        )

        eventRegistrationService.registerEvent(
            EventType.ERROR_WHILE_READING_PAYLOAD_FROM_DATABASE,
            requestId = referenceId,
            eventData = ex.toEventDataJson()
            // conversationId ikke tilgjengelig
        )
    }
    return@get
}

fun Routing.retryErrors(
    messageFilterService: MessageFilterService,
    failedMessageQueue: FailedMessageKafkaHandler
): Route =
    get("/api/retry/{$RETRY_LIMIT}") {
        if (!config().kafkaErrorQueue.active) {
            call.respondText(status = HttpStatusCode.ServiceUnavailable, text = "Retry not active.")
            return@get
        }
        failedMessageQueue.consumeRetryQueue(
            messageFilterService,
            limit = (call.parameters[RETRY_LIMIT])?.toInt() ?: 10
        )
        call.respondText(
            status = HttpStatusCode.OK,
            text = "Retry processing started with limit ${call.parameters[RETRY_LIMIT] ?: "default"}"
        )
    }

fun Routing.rerun(
    messageFilterService: MessageFilterService,
    failedMessageQueue: FailedMessageKafkaHandler
): Route =
    get("/api/rerun/{$KAFKA_OFFSET}") {
        if (!config().kafkaErrorQueue.active) {
            call.respondText(status = HttpStatusCode.ServiceUnavailable, text = "Retry queue not active.")
            return@get
        }
        val offsetParam = (call.parameters[KAFKA_OFFSET])?.toLong()
        if (offsetParam == null) {
            call.respondText(status = HttpStatusCode.BadRequest, text = "Must specify offset of message to rerun.")
            return@get
        }
        failedMessageQueue.forceRetryFailedMessage(
            messageFilterService,
            offset = offsetParam
        )
        call.respondText(
            status = HttpStatusCode.OK,
            text = "Message with offset ${call.parameters[KAFKA_OFFSET]} has been re-run"
        )
    }

fun Route.simulateError(
    failedMessageQueue: FailedMessageKafkaHandler
): Route =
    get("/api/forceretry/{$KAFKA_OFFSET}") {
        if (!config().kafkaErrorQueue.active) {
            call.respondText(status = HttpStatusCode.ServiceUnavailable, text = "Retry queue not active.")
            return@get
        }
        CoroutineScope(Dispatchers.IO).launch() {
            if (config().kafkaErrorQueue.active) {
                val record = getRecord(
                    config()
                        .kafkaPayloadReceiver.topic,
                    config().kafka
                        .copy(groupId = "ebms-provider-retry"),
                    (call.parameters[KAFKA_OFFSET])?.toLong() ?: 0
                )
                failedMessageQueue.sendToRetryQueueIncoming(
                    record = record ?: throw Exception("No Record found. Offset: ${call.parameters[KAFKA_OFFSET]}"),
                    reason = "Simulated Error"
                )
                call.respondText(
                    status = HttpStatusCode.OK,
                    text = "Payload message with offset ${call.parameters[KAFKA_OFFSET]} has been added to retry queue"
                )
            }
        }
    }

fun Route.unacknowledge(
    messagePendingAckRepository: MessagePendingAckRepository
): Route =
    get("/api/unacknowledge/{$MESSAGE_ID}") {
        val messageIdParam = call.parameters[MESSAGE_ID]
        if (messageIdParam.isNullOrBlank()) {
            call.respondText(status = HttpStatusCode.BadRequest, text = "Must specify message ID.")
            return@get
        }
        val updated = messagePendingAckRepository.unregisterAckForMessage(messageIdParam)
        if (updated) {
            log.info("Unacknowledged message $messageIdParam")
            call.respondText(
                status = HttpStatusCode.OK,
                text = "Acknowledgement for message $messageIdParam has been unset. Message will be resent."
            )
        } else {
            call.respondText(
                status = HttpStatusCode.NotFound,
                text = "No message found with ID $messageIdParam"
            )
        }
    }

fun Route.pauseRetries(
    pauseRetryErrorsTimerFlag: PauseRetryErrorsTimerFlag
): Route =
    get("/api/pauseretry") {
        CoroutineScope(Dispatchers.IO).launch() {
            pauseRetryErrorsTimerFlag.paused = true
        }
        log.info("Pausing retry task.")
        call.respond(StatusResponse("Error retries are PAUSED"))
    }

fun Route.resumeRetries(
    pauseRetryErrorsTimerFlag: PauseRetryErrorsTimerFlag
): Route =
    get("/api/resumeretry") {
        CoroutineScope(Dispatchers.IO).launch() {
            pauseRetryErrorsTimerFlag.paused = false
        }
        log.info("Resuming retry task.")
        call.respond(StatusResponse("Error retries are RESUMED"))
    }

fun Exception.getErrorMessage(): String {
    return localizedMessage ?: cause?.message ?: javaClass.simpleName
}
