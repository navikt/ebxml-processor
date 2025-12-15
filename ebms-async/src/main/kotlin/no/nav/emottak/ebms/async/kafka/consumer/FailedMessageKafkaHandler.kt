package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.Acks
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import no.nav.emottak.ebms.async.configuration.KafkaErrorQueue
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.processing.MessageFilterService
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaDuration

const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"
const val RETRY_REASON = "retryReason"

val logger = LoggerFactory.getLogger(FailedMessageKafkaHandler::class.java)

class FailedMessageKafkaHandler(
    val kafkaErrorQueue: KafkaErrorQueue = config().kafkaErrorQueue,
    kafka: Kafka = config().kafka
) {

    val publisher = KafkaPublisher(
        PublisherSettings(
            bootstrapServers = kafka.bootstrapServers,
            keySerializer = StringSerializer(),
            valueSerializer = ByteArraySerializer(),
            acknowledgments = Acks.All,
            properties = kafka.toProperties()
        )
    )

    private var errorTopicKafkaReceiver = KafkaReceiver(
        ReceiverSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = kafka.groupId,
            pollTimeout = 10.seconds,
            properties = kafka.toProperties()
        )
    )

    suspend fun sendToRetry(
        record: ReceiverRecord<String, ByteArray>,
        key: String = record.key(),
        value: ByteArray = record.value(),
        reason: String? = null
    ) {
        if (reason != null) {
            record.addHeader(RETRY_REASON, reason)
        }
        record.addHeader(RETRY_AFTER, getNextRetryTime(record))
        try {
            val metadata = publisher.publishScope {
                publish(ProducerRecord(config().kafkaErrorQueue.topic, null, key, value, record.headers()))
            }
            logger.info("Offset on metadata: " + metadata.offset())
            logger.info("Result " + metadata.partition() + " timestamp " + metadata.timestamp())
            logger.info("Message sent successfully to topic ${kafkaErrorQueue.topic}")
        } catch (e: Exception) {
            logger.info("Failed to send message to ${kafkaErrorQueue.topic} : ${e.message}")
        }
    }

    suspend fun consumeRetryQueue( // TODO refine retry logic
        messageFilterService: MessageFilterService,
        limit: Int = 10 // TODO default limit to offset
    ) {
        // TODO DefaultKafkaReceiver is too constrainted so need own impl for custom logic
        val consumer: Flow<ReceiverRecord<String, ByteArray>> =
            errorTopicKafkaReceiver.receive(kafkaErrorQueue.topic)

        logger.debug("Reading from error queue")
        var counter = 0
        var lastKey: String? = null
        CoroutineScope(Dispatchers.IO)
            .launch {
                consumer
                    .cancellable() // NOTE: cancellable() will ensure the flow is terminated before new items are emitted to collect { } if its job is cancelled, though flow builder and all implementations of SharedFlow are cancellable() by default.
                    .map { record ->
                        counter++
                        if (lastKey != null) {
                            if (lastKey == record.key()) {
                                logger.info("End of queue reached")
                                cancel("End of queue reached")
                            }
                        }
                        lastKey = record.key()
                        if (counter > limit) {
                            logger.info("Kafka retryQueue Limit reached: $limit")
                            cancel("Limit reached")
                            return@map
                        }
                        record.offset.acknowledge()
                        record.retryCounter()
                        val retryableAfter = Instant.parse(String(record.headers().lastHeader(RETRY_AFTER).value()))

                        if (Clock.System.now() > retryableAfter) {
                            messageFilterService.filterMessage(record)
                        } else {
                            logger.info("${record.key()} is not retryable yet.")
                            sendToRetry(record)
                        }
                        record.offset.commit()
                    }.collect()
            }
    }

    fun getNextRetryTime(record: ReceiverRecord<String, ByteArray>): String {
        if (record.headers().lastHeader(RETRY_AFTER) == null) {
            return Clock.System.now().toString()
        }
        return Clock.System.now().plus(5.minutes)
            .toString() // TODO create retry strategy
    }

    fun ReceiverRecord<String, ByteArray>.retryCounter(): Int {
        val lastHeader =
            String((headers().lastHeader(RETRY_COUNT_HEADER)?.value() ?: "0".toByteArray()))
                .takeIf { it.isNotBlank() } ?: "0"
        val retryCounter = lastHeader.toInt() + (1)
        this.headers().add(
            RETRY_COUNT_HEADER,
            retryCounter.toString().toByteArray()
        )
        return retryCounter
    }
}

fun ReceiverRecord<String, ByteArray>.addHeader(key: String, value: String) {
    this.headers().add(key, value.toByteArray())
}

fun getRetryRecord(fromOffset: Long = 0, requestedRecords: Int = 1): ReceiverRecord<String, ByteArray>? {
    return getRecord(config().kafkaErrorQueue.topic, config().kafka, fromOffset, requestedRecords)
}

fun getRecord(
    topic: String,
    kafka: Kafka,
    fromOffset: Long = 0,
    requestedRecords: Int = 1
): ReceiverRecord<String, ByteArray>? {
    return getRecords(topic, kafka, fromOffset, requestedRecords).firstOrNull()
}

fun getRecords(
    topic: String,
    kafka: Kafka,
    fromOffset: Long = 0,
    requestedRecords: Int = 1
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
            val kafkaRecords: ConsumerRecords<String, ByteArray> = consumer.poll(1.seconds.toJavaDuration())
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
