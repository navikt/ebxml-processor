package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.asReceiverRecord
import no.nav.emottak.ebms.async.kafka.consumer.getRetryInRecord
import no.nav.emottak.ebms.async.kafka.consumer.retryCount
import no.nav.emottak.ebms.async.kafka.consumer.retryCounter
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
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Instant
import java.time.LocalDateTime

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
        log.info("Decision [$decision]:\n" + "Failing payload sent at ${payloadMessage.sentAt ?: "unknown"}, error type: ${exception::class.simpleName ?: "Unknown error"}, reason: $retryReason, retries already performed: $retriedAlready. Decision reason: $reason")
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
    }

    suspend fun consumeRetryQueueIncoming(
        limit: Int,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        val records = failedMessageQueue.pollIncomingRetryRecords(limit)
        records.forEachIndexed { index, record  ->
            if (index >= limit) {
                log.info("Incoming retry queue limit reached: $limit")
                return@forEachIndexed
            }
            processRetryRecord(record, Direction.IN, processor)
            failedMessageQueue.commitOffset(record, Direction.IN)
        }
    }

    suspend fun consumeRetryQueueOutgoing(
        limit: Int,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        if (!config().kafkaErrorQueueOut.active) return
        val records = failedMessageQueue.pollOutgoingRetryRecords(limit)
        records.forEachIndexed { index, record ->
            if (index >= limit) {
                log.info("Outgoing retry queue limit reached: $limit")
                return@forEachIndexed
            }
            processRetryRecord(record, Direction.OUT, processor)
            failedMessageQueue.commitOffset(record, Direction.OUT)
        }
    }

    suspend fun forceRetryFailedMessage(
        offset: Long,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        log.info("Forcing re-run of message on incoming error queue at offset $offset")
        val record = getRetryInRecord(offset)
        if (record == null) {
            log.info("No record in incoming error queue at offset $offset")
            return
        }
        log.info("${record.key()} is being re-run.")
        processor(record)
        log.info("${record.key()} has been re-run.")
    }

    private suspend fun processRetryRecord(
        record: ConsumerRecord<String, ByteArray>,
        direction: Direction,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        val retryableAfter = failedMessageQueue.parseNextRetryHeader(record)
        log.info("Record with key ${record.key()} is retryable after $retryableAfter.")

        if (IGNORE_OLD_MESSAGES && LocalDateTime.now().minusDays(AGE_DAYS_TO_IGNORE).isAfter(retryableAfter)) {
            log.info("${record.key()} is too old, ignoring. This should only happen during DEV.")
            return
        }

        if (LocalDateTime.now().isAfter(retryableAfter)) {
            log.info("${record.key()} is being retried.")
            record.asReceiverRecord().retryCounter()
            processor(record.asReceiverRecord())
            log.info("${record.key()} has been retried.")
        } else {
            log.info("${record.key()} is not retryable yet.")
            when (direction) {
                Direction.IN -> failedMessageQueue.sendToRetryQueueIncoming(record.asReceiverRecord())
                Direction.OUT -> failedMessageQueue.sendToRetryQueueOutgoing(record.asReceiverRecord())
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

    companion object {
        // This flag is intended for dummy-processing old messages in the error queue on startup in DEV (approx. 3000).
        // It can also be used in normal operation to ignore messages older than the given number of days.
        const val IGNORE_OLD_MESSAGES = false
        const val AGE_DAYS_TO_IGNORE = 7L
    }
}
