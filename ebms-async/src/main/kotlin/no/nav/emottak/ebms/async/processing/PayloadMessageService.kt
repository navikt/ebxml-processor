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
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.marker
import no.nav.emottak.util.signatur.SignatureException
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
            when (exception) {
                is EbmsException -> {
                    runCatching {
                        returnMessageError(ebmsPayloadMessage, exception)
                    }.onFailure {
                        log.error(ebmsPayloadMessage.marker(), "Failed to return MessageError", exception)
                        // NB: Her sendes SELVE MELDINGEN til rekjøring, mens det er FEILMELDINGEN som feiler.
                        sendToRetryIfShouldBeRetried(record = record, payloadMessage = ebmsPayloadMessage, exception = exception, reason = "Failed to return MessageError: ${exception.message ?: "Unknown error"}")
                    }
                }
                is SignatureException -> {
                    log.error(ebmsPayloadMessage.marker(), exception.message, exception)
                    sendToRetryIfShouldBeRetried(record = record, payloadMessage = ebmsPayloadMessage, exception = exception, reason = exception.message)
                }
                else -> {
                    log.error(ebmsPayloadMessage.marker(), exception.message ?: "Unknown error", exception)
                    sendToRetryIfShouldBeRetried(record = record, payloadMessage = ebmsPayloadMessage, exception = exception, reason = exception.message ?: "Unknown error")
                }
            }
        }
    }

    // TODO under construction/experimentation, might be moved to a separate class
    private suspend fun sendToRetryIfShouldBeRetried(
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
        var shouldRetry = false
        var decisionReason = ""
        val ttl = payloadMessage.timeToLive

        // Error situations:
        //   Exception subtypes:
        //     CertificateValidationException
        //     CpaValidationException (errors during CPA processing)
        //     SecurityException (errors getting sec/signature props)
        //   CPA validation failure (incl signature)
        //   ProcessingService.processMessage results in error, incl retrieveReturnableApprecResponse returns null
        // Todo consider if any of these should have specific rules, for now we treat all equally

        if (isExpired(ttl)) {
            decisionReason = "ebXML TimeToLive expired at $ttl"
        } else {
            shouldRetry = true
            decisionReason = if (ttl != null) {
                "Within ebXML TimeToLive (expires at $ttl)"
            } else {
                "More retries OK, already retried $retriedAlready times"
            }
        }

        if (shouldRetry) {
            sendToRetry(record, reason)
            log.info("Schedule retry for failing payload sent at $sentAt, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
        } else {
            log.info("No retry for failing payload sent at $sentAt, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
            // If TTL explicitly expired, return a MessageError with TIME_TO_LIVE_EXPIRED; otherwise use generic decision reason
            if (ttl != null) {
                returnMessageError(payloadMessage, EbmsException("TimeToLive expired", errorCode = no.nav.emottak.message.model.ErrorCode.TIME_TO_LIVE_EXPIRED, exception = exception))
            } else {
                returnMessageError(payloadMessage, EbmsException(decisionReason, exception = exception))
            }
        }
    }

    private fun isExpired(ttl: Instant?): Boolean {
        return ttl?.let { Instant.now().isAfter(it) } ?: false
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

    private suspend fun sendToRetry(record: ReceiverRecord<String, ByteArray>, exceptionReason: String) {
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
