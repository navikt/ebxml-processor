package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.AGE_DAYS_TO_IGNORE
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.IGNORE_OLD_MESSAGES
import no.nav.emottak.ebms.async.kafka.consumer.asReceiverRecord
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
import java.time.Instant
import java.time.LocalDateTime

class RetryService(
    val cpaValidationService: CPAValidationService,
    val eventRegistrationService: EventRegistrationService,
    private val failedMessageQueue: FailedMessageKafkaHandler,
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

    suspend fun consumeRetryQueue(
        messageFilterService: MessageFilterService,
        limit: Int = 10
    ) {
        val records = failedMessageQueue.pollRetryQueue(limit)
        if (records.isEmpty()) {
            log.info("No records to process in error queue")
            return
        }
        log.info("At least ${records.count()} records to process in error queue")

        records.take(limit).forEachIndexed { index, record ->
            log.info("Processing record: ${index + 1}, max is $limit, key: ${record.key()}, offset: ${record.offset()}")
            val retryableAfter = failedMessageQueue.parseNextRetryHeader(record)

            log.info("Record with key ${record.key()} is retryable after $retryableAfter.")
            if (IGNORE_OLD_MESSAGES && LocalDateTime.now().minusDays(AGE_DAYS_TO_IGNORE).isAfter(retryableAfter)) {
                log.info("${record.key()} is too old, ignoring. This should only happen during DEV, when we want to process all messages in the queue.")
            } else if (LocalDateTime.now().isAfter(retryableAfter)) {
                log.info("${record.key()} is being retried.")
                record.asReceiverRecord().retryCounter()
                messageFilterService.filterMessage(record.asReceiverRecord())
                log.info("${record.key()} has been retried.")
            } else {
                log.info("${record.key()} is not retryable yet.")
                failedMessageQueue.sendToRetryQueueIncoming(record.asReceiverRecord(), advanceRetryTime = false)
            }
            failedMessageQueue.commitOffset(record)
        }
    }

    suspend fun forceRetryFailedMessage(
        messageFilterService: MessageFilterService,
        offset: Long
    ) {
        log.info("Forcing re-run of message on error queue at offset $offset")
        val record = failedMessageQueue.fetchRecord(offset)
        if (record == null) {
            log.info("No record in error queue at offset $offset")
            return
        }
        log.info("${record.key()} is being re-run.")
        messageFilterService.filterMessage(record)
        log.info("${record.key()} has been re-run.")
    }

    suspend fun sendToRetryQueueIncoming(
        record: ReceiverRecord<String, ByteArray>,
        reason: String
    ) {
        failedMessageQueue.sendToRetryQueueIncoming(record, reason)
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
