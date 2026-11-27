package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.publisher.Acks
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import no.nav.emottak.ebms.async.BackgroundJobMetrics
import no.nav.emottak.ebms.async.configuration.KafkaErrorQueue
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.incrementMessagesQueuedForRetry
import no.nav.emottak.ebms.async.leadership.BackgroundJobUnavailable
import no.nav.emottak.message.model.Direction
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.String
import kotlin.collections.map

const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"
const val RETRY_REASON = "retryReason"
const val REASON_FORCED_RETRY = "Forced Retry"

val logger: Logger = LoggerFactory.getLogger(FailedMessageKafkaHandler::class.java)

class FailedMessageKafkaHandler(
    val kafkaErrorQueue: KafkaErrorQueue = config().kafkaErrorQueue,
    val kafka: Kafka = config().kafka,
    val meterRegistry: MeterRegistry
) : AutoCloseable {

    val publisher = KafkaPublisher(
        PublisherSettings(
            bootstrapServers = kafka.bootstrapServers,
            keySerializer = StringSerializer(),
            valueSerializer = ByteArraySerializer(),
            acknowledgments = Acks.All,
            properties = kafka.toProperties()
        )
    )

    private val consumers = ConcurrentHashMap<Direction, RetryConsumer>()
    private val batchLocks = Direction.entries.associateWith { Mutex() }
    private val metrics by lazy {
        Direction.entries.associateWith {
            BackgroundJobMetrics(meterRegistry, "retry-${it.name.lowercase()}")
        }
    }

    suspend fun runRetryConsumer(direction: Direction, isLeader: () -> Boolean) {
        val outgoing = config().kafkaErrorQueueOut
        val topic = if (direction == Direction.IN) kafkaErrorQueue.topic else outgoing.topic
        val reset = if (direction == Direction.IN) kafkaErrorQueue.initOffset else outgoing.initOffset
        val properties = kafka.toProperties().apply {
            setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            setProperty(ConsumerConfig.GROUP_ID_CONFIG, "${kafka.groupId}-retry${if (direction == Direction.OUT) "-out" else ""}")
            setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, reset)
            setProperty(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000")
        }
        val owner = RetryConsumer(
            topic,
            { KafkaConsumer(properties, StringDeserializer(), ByteArrayDeserializer()) },
            isLeader,
            metrics.getValue(direction)::progress
        )
        check(consumers.putIfAbsent(direction, owner) == null) { "Retry consumer already running for $direction" }
        try {
            owner.run()
        } finally {
            consumers.remove(direction, owner)
        }
    }

    suspend fun consumeRetryRecords(
        direction: Direction,
        limit: Int,
        processor: suspend (ConsumerRecord<String, ByteArray>) -> Unit
    ) {
        try {
            withTimeout(10 * 60_000L) {
                batchLocks.getValue(direction).withLock {
                    val owner = consumers[direction] ?: throw BackgroundJobUnavailable("No leader retry consumer available for $direction")
                    metrics.getValue(direction).record { owner.consume(limit, processor) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw BackgroundJobUnavailable("Retry batch deadline exceeded; inspect progress before repeating")
        }
    }

    override fun close() = publisher.close()

    suspend fun sendToRetryQueueIncoming(
        record: ReceiverRecord<String, ByteArray>,
        reason: String? = null,
        nextRetryTime: String? = null
    ) {
        if (record.retryCount() > 0 && reason == REASON_FORCED_RETRY) {
            logger.warn("Message with key ${record.key()} already retried. Not retrying again. Reason: $reason")
            return
        }
        sendToRetry(
            record,
            reason = reason,
            nextRetryTime = nextRetryTime,
            direction = Direction.IN
        )
    }

    suspend fun sendToRetryQueueOutgoing(
        record: ReceiverRecord<String, ByteArray>,
        reason: String? = null,
        nextRetryTime: String? = null
    ) {
        sendToRetry(
            record,
            reason = reason,
            nextRetryTime = nextRetryTime,
            direction = Direction.OUT
        )
    }

    suspend fun sendToRetry(
        record: ReceiverRecord<String, ByteArray>,
        key: String = record.key(),
        value: ByteArray = record.value(),
        reason: String? = null,
        nextRetryTime: String? = null,
        direction: Direction
    ) {
        val topic = when (direction) {
            Direction.IN -> config().kafkaErrorQueue.topic
            Direction.OUT -> config().kafkaErrorQueueOut.topic
        }
        logger.info(
            "Sending message with key $key to $topic with reason: $reason"
        )
        if (reason != null) {
            record.addHeader(RETRY_REASON, reason)
        }
        if (nextRetryTime != null) {
            record.addHeader(RETRY_AFTER, nextRetryTime)
        }
        val metadata = publisher.publishScope {
            publish(
                ProducerRecord(
                    topic,
                    null,
                    key,
                    value,
                    record.headers()
                )
            )
        }
        logger.info("MESSAGE_QUEUED_FOR_RETRY in $topic with key $key at offset ${metadata.offset()}, retryAfter: $nextRetryTime")
        meterRegistry.incrementMessagesQueuedForRetry(topic)
    }

    fun parseNextRetryHeader(record: ConsumerRecord<String, ByteArray>): LocalDateTime {
        // Handles both old (Instant) and new (LocalDateTime) header formats.
        val header = try {
            String(record.headers().lastHeader(RETRY_AFTER).value())
        } catch (npe: NullPointerException) {
            logger.warn("No RETRY_AFTER header found, assuming immediate retry")
            return LocalDateTime.now()
        }
        return try {
            LocalDateTime.parse(header)
        } catch (e: Exception) {
            LocalDateTime.ofInstant(Instant.parse(header), ZoneId.systemDefault())
        }
    }
}

// Under test klarte vi å framprovosere en situasjon hvor headeren inneholdt en tekst
// som hverken ble oppfattet som blank eller lot seg parse som tall, derfor den omstendelige logikken her.
fun ReceiverRecord<String, ByteArray>.retryCount(): Int {
    if (headers().lastHeader(RETRY_COUNT_HEADER) == null) {
        return 0
    }
    val headerValue = String(headers().lastHeader(RETRY_COUNT_HEADER).value())
    if (cannotReadInt(headerValue)) {
        return 0
    }
    return headerValue.toInt()
}

fun ReceiverRecord<String, ByteArray>.retryCounter(): Int {
    val retryCounter = retryCount() + 1
    this.headers().add(
        RETRY_COUNT_HEADER,
        retryCounter.toString().toByteArray()
    )
    return retryCounter
}

private fun cannotReadInt(headerValue: String): Boolean {
    return headerValue.isBlank() || headerValue.toIntOrNull() == null
}

fun ReceiverRecord<String, ByteArray>.addHeader(key: String, value: String) {
    this.headers().add(key, value.toByteArray())
}

fun getRetryIncomingRecord(fromOffset: Long = 0, requestedRecords: Int = 1): ReceiverRecord<String, ByteArray>? {
    return getRecord(config().kafkaErrorQueue.topic, config().kafka, fromOffset, requestedRecords)
}

fun getRetryOutgoingRecord(fromOffset: Long = 0, requestedRecords: Int = 1): ReceiverRecord<String, ByteArray>? {
    return getRecord(config().kafkaErrorQueueOut.topic, config().kafka, fromOffset, requestedRecords)
}

fun getRecord(
    topic: String,
    kafka: Kafka,
    fromOffset: Long = 0,
    requestedRecords: Int = 1
): ReceiverRecord<String, ByteArray>? {
    return getRecords(topic, kafka, fromOffset, requestedRecords).firstOrNull()
}

fun repeatRecord(offset: String, topic: String, kafka: Kafka) {
    KafkaProducer(
        kafka.toProperties(),
        StringSerializer(),
        ByteArraySerializer()
    ).use { producer ->
        val record = getRecord(topic, kafka, fromOffset = offset.toLong()) ?: return@use
        producer.send(
            ProducerRecord(
                topic,
                null,
                record.key(),
                record.value(),
                record.headers()
            )
        )
    }
}

fun getRecords(
    topic: String,
    kafka: Kafka,
    fromOffset: Long = 0,
    requestedRecords: Int = 50
): List<ReceiverRecord<String, ByteArray>> {
    KafkaConsumer(
        kafka.toProperties(),
        StringDeserializer(),
        ByteArrayDeserializer()
    ).use { consumer ->
        // Seek
        consumer.partitionsFor(topic)
            .map { partition ->
                TopicPartition(partition.topic(), partition.partition())
            }.toList()
            .let { partitionList ->
                consumer.seekFromExactOffset(partitionList, fromOffset)
            }

        // Collect
        val recordList = ArrayList<ReceiverRecord<String, ByteArray>>()
        for (i in 0..requestedRecords) {
            val kafkaRecords: ConsumerRecords<String, ByteArray> = consumer.poll(Duration.ofSeconds(1))
            if (kafkaRecords.isEmpty) break
            kafkaRecords
                .filterNotNull()
                .forEach { record ->
                    recordList.add(record.asReceiverRecord())
                }
        }
        return recordList
    }
}

fun KafkaConsumer<String, ByteArray>.seekFromExactOffset(
    partitions: List<TopicPartition>,
    offset: Long
) {
    this.assign(partitions)
    partitions.forEach { tp ->
        val startOffset = this.beginningOffsets(listOf(tp))
            .firstNotNullOf { it.value }.takeIf { it > offset }
            .also { logger.info("Lowest offset is $it on partition ${tp.partition()}") }
            ?: offset
        this.seek(tp, startOffset)
    }
}

fun ConsumerRecord<String, ByteArray>.asReceiverRecord(): ReceiverRecord<String, ByteArray> {
    // Ugly workaround for ReceiverRecord / ConsumerRecord mapping compatibility
    class ReadOnlyOffset(
        override val offset: Long,
        override val topicPartition: TopicPartition
    ) : Offset {
        override suspend fun acknowledge() {
            throw Exception("Cannot acknowledge read only offset")
        }

        override suspend fun commit() {
            throw Exception("Cannot commit read only offset")
        }
    }
    return ReceiverRecord(
        this,
        ReadOnlyOffset(this.offset(), TopicPartition(this.topic(), this.partition()))
    )
}
