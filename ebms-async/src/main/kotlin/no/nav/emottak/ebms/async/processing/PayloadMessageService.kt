package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toKafkaHeaders
import no.nav.emottak.ebms.eventmanager.EventManagerService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.util.toByteArray
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType
import java.time.Instant

class PayloadMessageService(
    val cpaValidationService: CPAValidationService,
    val processingService: ProcessingService,
    val ebmsSignalProducer: EbmsMessageProducer,
    val payloadMessageForwardingService: PayloadMessageForwardingService,
    val eventRegistrationService: EventRegistrationService,
    val eventManagerService: EventManagerService,
    val failedMessageQueue: FailedMessageKafkaHandler
) {

    suspend fun process(
        record: ReceiverRecord<String, ByteArray>,
        ebmsPayloadMessage: PayloadMessage
    ) {
        runCatching {
            val isDuplicate = isDuplicateMessage(ebmsPayloadMessage)
            val isRetry = record.retryCount() > 0
            when (isDuplicate && !isRetry) {
                true -> log.info(ebmsPayloadMessage.marker(), "Got duplicate payload message with reference <${ebmsPayloadMessage.requestId}>")
                false -> processPayloadMessage(ebmsPayloadMessage)
            }
            returnAcknowledgment(ebmsPayloadMessage)
        }.onFailure { exception ->
            // TODO handle some errors by sending to retry, some by returning error message
            log.error(ebmsPayloadMessage.marker(), exception.message ?: "Message processing error", exception)
            sendToRetryIfShouldBeRetried(record = record, payloadMessage = ebmsPayloadMessage, exception = exception, reason = exception.message ?: "Unknown error")
        }
    }

    // TODO under construction/experimentation, might be moved to a separate class
    internal suspend fun sendToRetryIfShouldBeRetried(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        reason: String
    ) {
        // TODO this function should implement the rules for retrying messages
        // The exact reason why the message failed may be found in the exception
        // The time-to-live / expiry may be found from the message itself
        // The number of retries already done (if any) may be found from the record (headers)
        val retriedAlready = record.retryCount()
        val errorType = exception::class.simpleName ?: "Unknown error"
        val sentAt = payloadMessage.sentAt
        val ttl = payloadMessage.timeToLive

        // Error situations:
        //   Exception subtypes:
        //     CertificateValidationException
        //     CpaValidationException (errors during CPA processing)
        //     SecurityException (errors getting sec/signature props)
        //   CPA validation failure (incl signature)
        //   ProcessingService.processMessage results in error, incl retrieveReturnableApprecResponse returns null
        // Todo consider if any of these should have specific rules, for now we treat all equally

        val cfgMaxRetries = 10

        val (decision, decisionReason) = decideRetry(ttl = ttl, retriedAlready = retriedAlready, maxRetries = cfgMaxRetries)

        when (decision) {
            RetryDecision.RETRY -> {
                sendToRetry(record, reason)
                log.info("Schedule retry for failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
            }
            RetryDecision.TTL_EXPIRED -> {
                log.info("No retry for failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
                returnMessageError(payloadMessage, EbmsException("TimeToLive expired", errorCode = ErrorCode.TIME_TO_LIVE_EXPIRED, exception = exception))
            }
            RetryDecision.MAX_RETRIES_EXCEEDED -> {
                log.info("No retry for failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
                returnMessageError(payloadMessage, EbmsException(decisionReason, exception = exception))
            }
        }
    }

    internal fun decideRetry(ttl: Instant?, retriedAlready: Int, maxRetries: Int): Pair<RetryDecision, String> {
        if (ttl != null && isExpired(ttl)) {
            return RetryDecision.TTL_EXPIRED to "ebXML TimeToLive expired at $ttl"
        }
        if (retriedAlready >= maxRetries) {
            return RetryDecision.MAX_RETRIES_EXCEEDED to "Retried too many times, max number is $maxRetries, already retried $retriedAlready times"
        }
        val reason = if (ttl != null) {
            "Within ebXML TimeToLive (expires at $ttl), already retried $retriedAlready times"
        } else {
            "No ebXML TimeToLive but more retries OK, already retried $retriedAlready times"
        }
        return RetryDecision.RETRY to reason
    }

    internal enum class RetryDecision { RETRY, TTL_EXPIRED, MAX_RETRIES_EXCEEDED }

    internal fun isExpired(ttl: Instant): Boolean {
        return ttl <= Instant.now()
    }

    private suspend fun processPayloadMessage(ebmsPayloadMessage: PayloadMessage) {
        log.info(ebmsPayloadMessage.marker(), "Got payload message with reference <${ebmsPayloadMessage.requestId}>")
        eventRegistrationService.registerEventMessageDetails(ebmsPayloadMessage)
        val validationResult = cpaValidationService.validateIncomingMessage(ebmsPayloadMessage)
        val (processedPayload, direction) = processingService.processAsync(ebmsPayloadMessage, validationResult.payloadProcessing)
        when (direction) {
            Direction.IN -> payloadMessageForwardingService.forwardMessageWithSyncResponse(processedPayload)
            Direction.OUT -> payloadMessageForwardingService.returnMessageResponse(processedPayload)
        }
    }

    private suspend fun returnAcknowledgment(ebmsPayloadMessage: PayloadMessage) {
        val acknowledgment = ebmsPayloadMessage.createAcknowledgment().also {
            eventRegistrationService.registerEventMessageDetails(it)
        }
        val validationResult = cpaValidationService.validateOutgoingMessage(acknowledgment)
        sendResponseToTopic(
            acknowledgment.toEbmsDokument().signer(validationResult.payloadProcessing!!.signingCertificate),
            validationResult.signalEmailAddress
        )
        log.info(acknowledgment.marker(), "Acknowledgment returned")
    }

    suspend fun returnMessageError(ebmsPayloadMessage: EbmsMessage, ebmsException: EbmsException) {
        val messageError = ebmsPayloadMessage.createMessageError(ebmsException.feil).also {
            eventRegistrationService.registerEventMessageDetails(it)
        }
        val validationResult = cpaValidationService.validateOutgoingMessage(messageError)
        val signingCertificate = validationResult.payloadProcessing?.signingCertificate
        if (signingCertificate == null) {
            log.warn(messageError.marker(), "Could not find signing certificate for outgoing MessageError")
        } else {
            sendResponseToTopic(
                messageError.toEbmsDokument().signer(signingCertificate),
                validationResult.signalEmailAddress
            )
            log.warn(messageError.marker(), "MessageError returned", ebmsException)
        }
    }

    private suspend fun sendResponseToTopic(ebmsDocument: EbmsDocument, signalResponderEmails: List<EmailAddress>) {
        if (config().kafkaSignalProducer.active) {
            val messageHeader = ebmsDocument.messageHeader()
            try {
                log.info(messageHeader.marker(), "Sending message to Kafka queue")
                eventRegistrationService.runWithEvent(
                    successEvent = EventType.MESSAGE_PLACED_IN_QUEUE,
                    failEvent = EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                    requestId = ebmsDocument.requestId.parseOrGenerateUuid(),
                    messageId = ebmsDocument.messageHeader().messageData.messageId ?: "",
                    eventData = Json.encodeToString(
                        mapOf(EventDataType.QUEUE_NAME.value to config().kafkaSignalProducer.topic)
                    )
                ) {
                    ebmsSignalProducer.publishMessage(
                        key = ebmsDocument.requestId,
                        value = ebmsDocument.document.toByteArray(),
                        headers = signalResponderEmails.toKafkaHeaders() + messageHeader.toKafkaHeaders()
                    )
                }
            } catch (e: Exception) {
                log.error(messageHeader.marker(), "Exception occurred while sending message to Kafka queue", e)
            }
        }
    }

    internal suspend fun sendToRetry(record: ReceiverRecord<String, ByteArray>, exceptionReason: String) {
        if (config().kafkaSignalProducer.active && config().kafkaPayloadProducer.active && config().kafkaErrorQueue.active) {
            failedMessageQueue.sendToRetry(
                record = record,
                reason = exceptionReason
            )
        }
    }

    suspend fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean {
        val duplicateEliminationStrategy = try {
            cpaValidationService.getDuplicateEliminationStrategy(ebmsPayloadMessage)
        } catch (e: Exception) {
            log.warn(ebmsPayloadMessage.marker(), "Error checking duplicate status", e)
            return false
        }

        if (duplicateEliminationStrategy == PerMessageCharacteristicsType.ALWAYS) {
            return eventManagerService.isDuplicateMessage(ebmsPayloadMessage)
        }

        if (
            duplicateEliminationStrategy == PerMessageCharacteristicsType.PER_MESSAGE &&
            ebmsPayloadMessage.duplicateElimination
        ) {
            return eventManagerService.isDuplicateMessage(ebmsPayloadMessage)
        }
        return false
    }
}
