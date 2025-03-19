package no.nav.emottak.ebms.messaging

import io.github.nomisRev.kafka.Acks
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.kafkaProducer
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.configuration.Kafka
import no.nav.emottak.ebms.configuration.KafkaErrorQueue
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.configuration.toProperties
import no.nav.emottak.ebms.processing.PayloadMessageProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
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
    private var producersFlow: Flow<KafkaProducer<String, ByteArray>> = kafkaProducer(
        ProducerSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringSerializer(),
            valueDeserializer = ByteArraySerializer(),
            acks = Acks.All,
            other = kafka.toProperties()
        )
    )
    private var consumerFlow: Flow<ReceiverRecord<String, ByteArray>> = KafkaReceiver(
        ReceiverSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = kafka.groupId,
            pollTimeout = 10.seconds,
            properties = kafka.toProperties()
        )
    ).receive(kafkaErrorQueue.topic)

    suspend fun send(record: ReceiverRecord<String, ByteArray>, key: String = record.key(), value: ByteArray = record.value()) { // TODO man trenger vel ikke egentlig value og key om man har record?
        record.addHeader(RETRY_AFTER, getNextRetryTime(record)) // TODO add retry logic
        try {
            producersFlow.collect { producer ->
                producer.send(ProducerRecord(kafkaErrorQueue.topic, null, key, value, record.headers())).get()
            }
            logger.debug("Kafka test: Message sent successfully to topic ${kafkaErrorQueue.topic}")
        } catch (e: Exception) {
            logger.debug("Kafka test: Failed to send message: ${e.message}")
        }
    }

    suspend fun receive(payloadMessageProcessor: PayloadMessageProcessor, limit: Int = 10) { // TODO limit til offset
        logger.debug("Reading from error queue")
        var counter = 0
        consumerFlow.map { record ->
            counter++
            if (counter > limit) {
                throw Exception("Error queue limit exceeded: $limit") // TODO fjern dette
            }
            record.offset.acknowledge()
            record.retryCounter()
            payloadMessageProcessor.process(record)
            record.offset.acknowledge()
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

fun getRecord2(topic: String, kafka: Kafka, fromOffset: Long = 0, requestedRecords: Int = 1) {
    val consumer = KafkaConsumer(
        kafka.copy(
            groupId = "ebms-provider-retry"
        ).toProperties(),
        StringDeserializer(),
        ByteArrayDeserializer()
    )

    consumer.partitionsFor(topic)
}

fun getRecord(topic: String, kafka: Kafka, fromOffset: Long = 0, requestedRecords: Int = 1): ReceiverRecord<String, ByteArray>? {
    return with(
        KafkaConsumer(
//            kafka.copy(
//                groupId = "ebms-provider-retry"
//            ).toProperties(),
            kafka.toProperties(),
            StringDeserializer(),
            ByteArrayDeserializer()
        )
    ) {
        partitionsFor(topic)
            .map { partition ->
                TopicPartition(partition.topic(), partition.partition())
            }.toList().apply {
                assign(this)
                this.forEach { tp ->
                    val startOffset = beginningOffsets(listOf(tp))
                        .firstNotNullOf { it.value }.takeIf { it > fromOffset }.also { logger.info("Lowest offset is $it") }
                        ?: fromOffset
                    seek(tp, startOffset)
                }
            }

        for (i in 0..requestedRecords) {
            val kafkaRecords = poll(Duration.ofSeconds(1))
            if (kafkaRecords.isEmpty) break
            return@with getReceiverRecord(kafkaRecords.records(topic).firstOrNull())
        }
        return null
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
    return ReceiverRecord(consumerRecord, ReadOnlyOffset(consumerRecord.offset(), TopicPartition(consumerRecord.topic(), consumerRecord.partition())))
}
