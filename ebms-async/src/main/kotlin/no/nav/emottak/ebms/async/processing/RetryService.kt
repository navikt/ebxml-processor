package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
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

        val retriedAlready = record.retryCount()
        val errorType = exception::class.simpleName ?: "Unknown error"
        val sentAt = payloadMessage.sentAt

        when (direction) {
            Direction.IN -> {
                val ttl = payloadMessage.timeToLive

                val (decision, decisionReason) = decideRetry(
                    ttl = ttl,
                    retriedAlready = retriedAlready,
                    maxRetries = config().errorRetryPolicy.maxRetriesIn
                )

                val logMsg = "Failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: $decisionReason"

                if (decision == RetryDecision.RETRY) {
                    sendToRetryIn(record, reason, direction)
                    log.info("Schedule retry: $logMsg")
                } else {
                    log.info("No retry: $logMsg")
                    val ebmsException = when (decision) {
                        RetryDecision.TTL_EXPIRED -> EbmsException(
                            "TimeToLive expired",
                            errorCode = no.nav.emottak.message.model.ErrorCode.TIME_TO_LIVE_EXPIRED,
                            exception = exception
                        )
                        else -> EbmsException(decisionReason, exception = exception)
                    }
                    try {
                        returnMessageError(payloadMessage, ebmsException)
                    } catch (e: Exception) {
                        log.error(
                            "Failed to return Message error for payload message ${payloadMessage.requestId}, sender WILL NOT BE NOTIFIED that the message has NOT been processed OK",
                            e
                        )
                    }
                }
            }

            // TODO: resolved a comment without intention - yes, only a question if we want to have separate requirements

            Direction.OUT -> {
                if (retriedAlready >= config().errorRetryPolicy.maxRetriesOut) {
                    val logMsg = "Failing payload sent at ${sentAt ?: "unknown"}, error type: $errorType, reason: $reason, retries already performed: $retriedAlready. Decision reason: Max retries exceeded"
                    log.info("No retry: $logMsg")
                } else {
                    sendToRetryOut(record, reason, direction)
                }
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

    internal suspend fun sendToRetryIn(record: ReceiverRecord<String, ByteArray>, exceptionReason: String, direction: Direction) {
        // TODO are there cases where these are not set to active? testing?
        if (config().kafkaSignalProducer.active && config().kafkaPayloadProducer.active && config().kafkaErrorQueue.active) {
            failedMessageQueue.sendToRetry(
                record = record,
                reason = exceptionReason,
                direction = direction
            )
        }
    }

    internal suspend fun sendToRetryOut(record: ReceiverRecord<String, ByteArray>, exceptionReason: String, direction: Direction) {
        log.warn("Sending message to retry out queue with reason: $exceptionReason")
        if (config().kafkaErrorQueueOut.active) {
            failedMessageQueue.sendToRetry(
                record = record,
                reason = exceptionReason,
                direction = direction
            )
        }
    }
}
