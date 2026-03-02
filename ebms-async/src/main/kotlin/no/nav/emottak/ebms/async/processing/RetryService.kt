package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.marker
import java.time.Instant

class RetryService(
    val cpaValidationService: CPAValidationService,
    val eventRegistrationService: EventRegistrationService,
    val failedMessageQueue: FailedMessageKafkaHandler,
    val signalSender: suspend (EbmsDocument, List<EmailAddress>) -> Unit
) {

    internal suspend fun sendToRetryIfShouldBeRetried(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        reason: String,
        direction: Direction
    ) {
        // TODO what to reuse, maxRetries?
        if (direction == Direction.IN) {
            val retriedAlready = record.retryCount()
            val errorType = exception::class.simpleName ?: "Unknown error"
            val sentAt = payloadMessage.sentAt
            val ttl = payloadMessage.timeToLive

            val cfgMaxRetries = 10

            val (decision, decisionReason) = decideRetry(ttl = ttl, retriedAlready = retriedAlready, maxRetries = cfgMaxRetries)

            when (decision) {
                RetryDecision.RETRY -> {
                    sendToRetryIn(record, reason)
                    log.info("Schedule retry for failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
                }
                RetryDecision.TTL_EXPIRED -> {
                    log.info("No retry for failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
                    try {
                        returnMessageError(payloadMessage, EbmsException("TimeToLive expired", errorCode = no.nav.emottak.message.model.ErrorCode.TIME_TO_LIVE_EXPIRED, exception = exception))
                    } catch (e: Exception) {
                        log.error("Failed to return Message error for payload message ${payloadMessage.requestId}, sender WILL NOT BE NOTIFIED that the message has NOT been processed OK", e)
                    }
                }
                RetryDecision.MAX_RETRIES_EXCEEDED -> {
                    log.info("No retry for failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason")
                    try {
                        returnMessageError(payloadMessage, EbmsException(decisionReason, exception = exception))
                    } catch (e: Exception) {
                        log.error("Failed to return Message error for payload message ${payloadMessage.requestId}, sender WILL NOT BE NOTIFIED that the message has NOT been processed OK", e)
                    }
                }
            }
        } else {
            sendToRetryOut(record, reason)
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

    suspend fun returnMessageError(ebmsPayloadMessage: EbmsMessage, ebmsException: EbmsException) {
        val messageError = ebmsPayloadMessage.createMessageError(ebmsException.feil).also {
            eventRegistrationService.registerEventMessageDetails(it)
        }
        val validationResult = cpaValidationService.validateOutgoingMessage(messageError)
        val signingCertificate = validationResult.payloadProcessing?.signingCertificate
        signalSender(
            messageError.toEbmsDokument().signer(signingCertificate!!),
            validationResult.signalEmailAddress
        )
        log.warn(messageError.marker(), "MessageError returned", ebmsException)
    }

    internal suspend fun sendToRetryIn(record: ReceiverRecord<String, ByteArray>, exceptionReason: String) {
        if (config().kafkaSignalProducer.active && config().kafkaPayloadProducer.active && config().kafkaErrorQueue.active) {
            failedMessageQueue.sendToRetryInbound(
                record = record,
                reason = exceptionReason
            )
        }
    }

    // TODO
    internal suspend fun sendToRetryOut(record: ReceiverRecord<String, ByteArray>, exceptionReason: String) {
        log.warn("Sending message to retry out queue with reason: $exceptionReason")
        // TODO check if all of them needed
        if (config().kafkaSignalProducer.active && config().kafkaPayloadProducer.active && config().kafkaErrorQueue.active) {
            failedMessageQueue.sendToRetryOutbound(
                record = record,
                reason = exceptionReason
            )
        }
    }
}
