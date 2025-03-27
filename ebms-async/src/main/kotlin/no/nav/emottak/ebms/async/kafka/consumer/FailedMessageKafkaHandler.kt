package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.Acks
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.async.configuration.Kafka
import no.nav.emottak.ebms.async.configuration.KafkaErrorQueue
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.processing.PayloadMessageProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

val failedMessageQueue: FailedMessageKafkaHandler = FailedMessageKafkaHandler()
const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"

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
        value: ByteArray = record.value()
    ) {
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
        payloadMessageProcessor: PayloadMessageProcessor,
        limit: Int = 10 // TODO default limit to offset
    ) {
        // TODO DefaultKafkaReceiver is too constrainted so need own impl for custom logic
        val consumer: Flow<ReceiverRecord<String, ByteArray>> =
            errorTopicKafkaReceiver.receive(kafkaErrorQueue.topic)

        logger.debug("Reading from error queue")
        var counter = 0
        consumer.map { record ->
            counter++
            if (counter > limit) {
                logger.info("Kafka retryQueue Limit reached: $limit")
                return@map
            }
            record.offset.acknowledge()
            record.retryCounter()
            if (DateTime.parse(
                    String(record.headers().lastHeader(RETRY_AFTER).value())
                ).isAfter(DateTime.now())
            ) {
                payloadMessageProcessor.process(record)
            } else {
                logger.info("${record.key()} is not retryable yet.")
                failedMessageQueue.sendToRetry(record)
            }
            record.offset.commit()
        }.collect()
    }

    fun getNextRetryTime(record: ReceiverRecord<String, ByteArray>): String {
        return DateTime.now().plusMinutes(5)
            .toString() // TODO create retry strategy
    }

    fun ReceiverRecord<String, ByteArray>.retryCounter(): BigInteger {
        val lastHeader = headers().lastHeader(RETRY_COUNT_HEADER)?.value() ?: (0).toBigInteger().toByteArray()
        val retryCounter = BigInteger(lastHeader) + (1).toBigInteger()
        this.headers().add(
            RETRY_COUNT_HEADER,
            retryCounter.toByteArray()
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
            val kafkaRecords: ConsumerRecords<String, ByteArray> = consumer.poll(Duration.ofSeconds(1))
            if (kafkaRecords.isEmpty) break
            kafkaRecords
                .filterNotNull()
                .forEach { record ->
                    recordList.add(getReceiverRecord(record)!!)
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

fun getReceiverRecord(consumerRecord: ConsumerRecord<String, ByteArray>?): ReceiverRecord<String, ByteArray>? {
    if (consumerRecord == null) return null
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
        consumerRecord,
        ReadOnlyOffset(consumerRecord.offset(), TopicPartition(consumerRecord.topic(), consumerRecord.partition()))
    )
}
