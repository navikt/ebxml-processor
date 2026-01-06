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
import no.nav.emottak.ebms.async.configuration.ErrorRetryPolicy
import no.nav.emottak.ebms.async.configuration.KafkaErrorQueue
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.processing.MessageFilterService
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.clients.consumer.ConsumerConfig
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
import java.time.Duration
import java.time.LocalDateTime
import java.util.Properties
import kotlin.collections.map
import kotlin.time.Duration.Companion.seconds

const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"
const val RETRY_REASON = "retryReason"

// Dette flagget er i utgangspunktet satt på for å dummy-prosessere alle gamle meldinger i feilkøen ved oppstart i DEV (ca 3000)
// men kan evt brukes også i vanlig kjøring, siden det ignorerer meldinger eldre enn 1 uke
const val IGNORE_OLD_MESSAGES = true
const val AGE_DAYS_TO_IGNORE = 7

val logger = LoggerFactory.getLogger(FailedMessageKafkaHandler::class.java)

class FailedMessageKafkaHandler(
    val kafkaErrorQueue: KafkaErrorQueue = config().kafkaErrorQueue,
    kafka: Kafka = config().kafka,
    val errorRetryPolicy: ErrorRetryPolicy = config().errorRetryPolicy
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
            pollTimeout = 1.seconds,
            properties = kafka.toProperties()
        )
    )

    // Set up a consumer only used to check if there are any messages in the error queue, NOT committing the read offset
    val pollerConsumer = KafkaConsumer(
        getPollerProperties(kafka.toProperties(), kafka.groupId),
        StringDeserializer(),
        ByteArrayDeserializer()
    ).also { consumer ->
        val partitions = consumer.partitionsFor(kafkaErrorQueue.topic).map { TopicPartition(it.topic(), it.partition()) }
        consumer.assign(partitions)
    }

    fun getPollerProperties(properties: Properties, groupId: String): Properties {
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        return properties
    }

    suspend fun sendToRetry(
        record: ReceiverRecord<String, ByteArray>,
        key: String = record.key(),
        value: ByteArray = record.value(),
        reason: String? = null,
        advanceRetryTime: Boolean = true
    ) {
        if (reason != null) {
            record.addHeader(RETRY_REASON, reason)
        }
        if (advanceRetryTime) {
            record.addHeader(RETRY_AFTER, getNextRetryTime(record))
        }
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

    var loggingId = 0
    suspend fun consumeRetryQueue(
        messageFilterService: MessageFilterService,
        limit: Int = 10
    ) {
        loggingId++
        val startupTime = LocalDateTime.now()
        logger.info("Checking for messages in error queue, run id: $loggingId with startup time: $startupTime.")
        val anyRecords = anyRecordsToConsume(pollerConsumer)
        if (!anyRecords) {
            return
        }

        var counter = 0
        val processedKeys: MutableSet<String> = HashSet()
        CoroutineScope(Dispatchers.IO)
            .launch {
                // TODO DefaultKafkaReceiver is too constrainted so need own impl for custom logic
                val consumer: Flow<ReceiverRecord<String, ByteArray>> =
                    errorTopicKafkaReceiver.receive(kafkaErrorQueue.topic)
                consumer
                    .cancellable() // NOTE: cancellable() will ensure the flow is terminated before new items are emitted to collect { } if its job is cancelled, though flow builder and all implementations of SharedFlow are cancellable() by default.
                    .map { record ->
                        counter++
                        if (processedKeys.contains(record.key())) {
                            logger.info("All messages retried, end of queue reached, run id: $loggingId with startup time: $startupTime.")
                            cancel("End of queue reached.")
                            return@map
                        }

                        processedKeys.add(record.key())
                        if (counter > limit) {
                            logger.info("Kafka retryQueue Limit reached: $limit, run id: $loggingId with startup time: $startupTime.")
                            cancel("Limit reached")
                            return@map
                        }

                        logger.info("Processing record: $counter, max is $limit, key: ${record.key()}, offset: ${record.offset()}, run id: $loggingId with startup time: $startupTime.")
                        record.offset.acknowledge()
                        record.retryCounter()
                        val retryableAfter = DateTime.parse(
                            String(record.headers().lastHeader(RETRY_AFTER).value())
                        )
                        if (DateTime.now().isAfter(retryableAfter)) {
                            messageFilterService.filterMessage(record)
                            logger.info("${record.key()} has been retried.")
                        } else {
                            logger.info("${record.key()} is not retryable yet.")
                            sendToRetry(record, advanceRetryTime = false)
                        }
                        record.offset.commit()
                    }.collect()
            }
    }

    fun getNextRetryTime(record: ReceiverRecord<String, ByteArray>): String {
        val nextIntervalMinutes = errorRetryPolicy.nextIntervalMinutes(record.retryCount())
        return DateTime.now().plusMinutes(nextIntervalMinutes).toString()
    }

    fun ReceiverRecord<String, ByteArray>.retryCounter(): Int {
        val retryCounter = retryCount() + 1
        this.headers().add(
            RETRY_COUNT_HEADER,
            retryCounter.toString().toByteArray()
        )
        return retryCounter
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
private fun cannotReadInt(headerValue: String): Boolean {
    return headerValue.isBlank() || headerValue.toIntOrNull() == null
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
                    recordList.add(record.asReceiverRecord())
                }
        }
        return recordList
    }
}

fun anyRecordsToConsume(
    pollerConsumer: KafkaConsumer<String, ByteArray>
): Boolean {
    val records = pollerConsumer.poll(Duration.ofMillis(1000))
    logger.info("${records.count()} records to process in error queue")
    return (!records.isEmpty)
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
