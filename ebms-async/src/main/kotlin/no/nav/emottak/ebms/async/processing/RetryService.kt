package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.configuration.ErrorRetryPolicy
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.asReceiverRecord
import no.nav.emottak.ebms.async.kafka.consumer.getRetryRecord
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
import kotlin.time.toJavaDuration

class RetryService(
    val cpaValidationService: CPAValidationService,
    val eventRegistrationService: EventRegistrationService,
    val failedMessageKafkaHandler: FailedMessageKafkaHandler,
    val signalSender: suspend (EbmsDocument, List<EmailAddress>) -> Unit
) {

    internal suspend fun incomingRetryEval(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        retryReason: String = exception.message ?: "Unknown error"
    ) {
        val retryCount = record.retryCount()
        val (decision, reason) = decideRetry(
            ttl = payloadMessage.timeToLive,
            retriedAlready = retryCount,
            maxRetries = config().errorRetryPolicyIncoming.maxRetries
        )
        when (decision) {
            RetryDecision.RETRY -> failedMessageKafkaHandler.sendToRetryQueueIncoming(record, retryReason, getNextRetryTime(record, config().errorRetryPolicyIncoming))
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
                    exception as? EbmsException
                        ?: EbmsException(
                            "Max Retries expired",
                            errorCode = no.nav.emottak.message.model.ErrorCode.DELIVERY_FAILURE,
                            exception = exception
                        )
                )
        }
        log.info("Decision [$decision]:\n" + "Failing payload sent at ${payloadMessage.sentAt ?: "unknown"}, error type: ${exception::class.simpleName ?: "Unknown error"}, reason: $retryReason, retries already performed: $retryCount. Decision reason: $reason")
    }

    internal suspend fun outgoingRetryEval(
        record: ReceiverRecord<String, ByteArray>,
        payloadMessage: PayloadMessage,
        exception: Throwable,
        retryReason: String = exception.message ?: "Unknown error"
    ) {
        val retryCount = record.retryCount()
        val (decision, reason) = decideRetry(
            ttl = payloadMessage.timeToLive,
            retriedAlready = retryCount,
            maxRetries = config().errorRetryPolicyOutgoing.maxRetries
        )
        when (decision) {
            RetryDecision.RETRY -> failedMessageKafkaHandler.sendToRetryQueueOutgoing(record, retryReason, getNextRetryTime(record, config().errorRetryPolicyOutgoing))
            RetryDecision.TTL_EXPIRED -> log.error(payloadMessage.marker(), "ebXML TimeToLive expired for outgoing message", exception)
            RetryDecision.MAX_RETRIES_EXCEEDED -> log.error(payloadMessage.marker(), "ebXML Max Retires exhausted for outgoing message", exception)
        }
        log.info("Decision [$decision]:\n" + "Failing outgoing payload sent at ${payloadMessage.sentAt ?: "unknown"}, error type: ${exception::class.simpleName ?: "Unknown error"}, reason: $retryReason, retries already performed: $retryCount. Decision reason: $reason")
    }

    /**
     * Polls the incoming retry queue and processes each record.
     * Records that are ready to retry are handed to [processor].
     * Records not yet due are re-queued without advancing their retry time.
     *
     * @param limit Maximum number of records to process.
     * @param processor Called to re-process each ready record (e.g. messageFilterService::filterMessage).
     */
    suspend fun consumeRetryQueueIncoming(
        limit: Int,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        val records = failedMessageKafkaHandler.pollIncomingRetryRecords(limit)
        records.forEach { record ->
            processRetryRecord(record, Direction.IN, processor)
            failedMessageKafkaHandler.commitOffset(record, Direction.IN)
        }
    }

    /**
     * Polls the outgoing retry queue and processes each record.
     * Records that are ready to retry are handed to [processor].
     * Records not yet due are re-queued without advancing their retry time.
     *
     * @param limit Maximum number of records to process.
     * @param processor Called to re-process each ready record.
     */
    suspend fun consumeRetryQueueOutgoing(
        limit: Int,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        if (!config().kafkaErrorQueueOut.active) return
        val records = failedMessageKafkaHandler.pollOutgoingRetryRecords(limit)
        records.forEach { record ->
            processRetryRecord(record, Direction.OUT, processor)
            failedMessageKafkaHandler.commitOffset(record, Direction.OUT)
        }
    }

    /**
     * Forces re-processing of a specific message from the incoming retry queue by offset.
     * The retry counter is NOT incremented — this is an operator-initiated re-run.
     *
     * @param offset Kafka offset of the message on the incoming retry queue.
     * @param processor Called to re-process the record.
     */
    suspend fun forceRetryFailedMessage(
        offset: Long,
        processor: suspend (ReceiverRecord<String, ByteArray>) -> Unit
    ) {
        log.info("Forcing re-run of message on incoming error queue at offset $offset")
        val record = getRetryRecord(offset)
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
        val retryableAfter = failedMessageKafkaHandler.parseNextRetryHeader(record)
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
                Direction.IN -> failedMessageKafkaHandler.sendToRetryQueueIncoming(record.asReceiverRecord())
                Direction.OUT -> failedMessageKafkaHandler.sendToRetryQueueOutgoing(record.asReceiverRecord())
            }
        }
    }

    internal fun getNextRetryTime(record: ReceiverRecord<String, ByteArray>, policy: ErrorRetryPolicy): String {
        val nextInterval = policy.nextInterval(record.retryCount())
        return LocalDateTime.now().plus(nextInterval.toJavaDuration()).toString()
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
