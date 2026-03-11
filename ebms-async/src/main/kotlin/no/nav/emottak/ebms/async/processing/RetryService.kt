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

    internal suspend fun incomingRetryEval(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        retryReason: String = exception.message ?: "Unknown error"
    ) {
        sendToRetryIfShouldBeRetried(record, payloadMessage, exception, retryReason, Direction.IN)
    }

    internal suspend fun outgoingRetryEval(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        retryReason: String = exception.message ?: "Unknown error"
    ) {
        sendToRetryIfShouldBeRetried(record, payloadMessage, exception, retryReason, Direction.OUT)
    }

    internal suspend fun sendToRetryIfShouldBeRetried(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        retryReason: String,
        direction: Direction
    ) {
        val retriedAlready = record.retryCount()
        val (decision, reason) = decideRetry(
            ttl = payloadMessage.timeToLive,
            retriedAlready = retriedAlready,
            maxRetries = when (direction) {
                Direction.IN -> config().errorRetryPolicyIncoming.maxRetries
                Direction.OUT -> config().errorRetryPolicyOutgoing.maxRetries
            }
        )
        when (decision) {
            RetryDecision.RETRY -> {
                when (direction) {
                    Direction.OUT -> failedMessageQueue.sendToRetryQueueOutgoing(record, retryReason)
                    Direction.IN -> failedMessageQueue.sendToRetryQueueIncoming(record, retryReason)
                }
            }
            RetryDecision.TTL_EXPIRED ->
                returnMessageError(
                    payloadMessage,
                    EbmsException(
                        "TimeToLive expired",
                        errorCode = no.nav.emottak.message.model.ErrorCode.TIME_TO_LIVE_EXPIRED,
                        exception = exception
                    )
                )
            RetryDecision.MAX_RETRIES_EXCEEDED ->
                returnMessageError(
                    payloadMessage,
                    EbmsException(
                        "Max Retries expired",
                        errorCode = no.nav.emottak.message.model.ErrorCode.DELIVERY_FAILURE,
                        exception = exception
                    )
                )
        }
        log.info("Decision [$decision]:\n" + "Failing payload sent at ${payloadMessage.sentAt ?: "unknown"}, error type: ${exception::class.simpleName ?: "Unknown error"}, reason: $retryReason, retries already performed: $retriedAlready. Decision reason: $reason")
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
}
