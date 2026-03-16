package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.publisher.Acks
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import no.nav.emottak.ebms.async.configuration.KafkaErrorQueue
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.message.model.Direction
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
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
import java.util.Properties
import kotlin.String
import kotlin.collections.map

const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"
const val RETRY_REASON = "retryReason"

val logger: Logger = LoggerFactory.getLogger(FailedMessageKafkaHandler::class.java)

class FailedMessageKafkaHandler(
    val kafkaErrorQueue: KafkaErrorQueue = config().kafkaErrorQueue,
    val kafka: Kafka = config().kafka
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

    // Set up a consumer for POLLing the retry topic. NOTE that this only polls and disconnects, it is NOT listening forever like the receivers.
    // We get into troubles with committing if we use the same groupid as the receivers used to listen on the message topics.
    val groupIdForRetry = kafka.groupId + "-retry"
    val inPollerConsumer = KafkaConsumer(
        getPollerProperties(
            kafka.toProperties(),
            groupIdForRetry,
            config().errorRetryPolicyIncoming.processInterval.inWholeSeconds,
            kafkaErrorQueue.initOffset
        ),
        StringDeserializer(),
        ByteArrayDeserializer()
    ).also { c ->
        val partitions = c.partitionsFor(kafkaErrorQueue.topic).map { TopicPartition(it.topic(), it.partition()) }
        c.assign(partitions)
    }

    val outPollerConsumer = KafkaConsumer(
        getPollerProperties(
            kafka.toProperties(),
            "$groupIdForRetry-out",
            config().errorRetryPolicyOutgoing.processInterval.inWholeSeconds,
            config().kafkaErrorQueueOut.initOffset
        ),
        StringDeserializer(),
        ByteArrayDeserializer()
    ).also { c ->
        val partitions = c.partitionsFor(config().kafkaErrorQueueOut.topic)
            .map { TopicPartition(it.topic(), it.partition()) }
        c.assign(partitions)
    }

    fun getPollerProperties(
        properties: Properties,
        groupId: String,
        pollIntervalSeconds: Long,
        initOffset: String
    ): Properties {
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        // Sikre at ikke Kafka anser polleren vår som "død" selv om den ikke poller så ofte som Kafka er designet for.
        // Det vil føre til uønsket rebalansering etc, og vil tydeligvis også skje med bruk av assign() istedenfor subscribe()
        val millisAllowedBetweenPolls = (pollIntervalSeconds * 1000) + 60000
        properties.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, millisAllowedBetweenPolls.toString())
        // Hvis denne har verdi "earliest" vil man prosessere ALLE meldingene på feilkøen fra tidligste offset, første gangen app'en startes
        // Med "latest" vil man regne alle meldingene på feilkøen som allerede prosessert, og fortsette prosessering når nye meldinger kommer
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, initOffset)
        return properties
    }

    fun pollIncomingRetryRecords(limit: Int): List<ConsumerRecord<String, ByteArray>> {
        logger.info(
            "Checking for messages in error queue, current offset " + inPollerConsumer.position(
                TopicPartition(kafkaErrorQueue.topic, 0)
            )
        )
        val records = getRecordsToConsume(inPollerConsumer, limit)
        if (records.isEmpty()) {
            logger.info("No records to process in incoming error queue")
        } else {
            logger.info("At least ${records.count()} records to process in incoming error queue")
        }
        return records
    }

    fun pollOutgoingRetryRecords(limit: Int): List<ConsumerRecord<String, ByteArray>> {
        logger.info(
            "Checking for messages in outgoing error queue, current offset " + outPollerConsumer.position(
                TopicPartition(config().kafkaErrorQueueOut.topic, 0)
            )
        )
        val records = getRecordsToConsume(outPollerConsumer, limit)
        if (records.isEmpty()) {
            logger.info("No records to process in outgoing error queue")
        } else {
            logger.info("At least ${records.count()} records to process in outgoing error queue")
        }
        return records
    }

    fun commitOffset(record: ConsumerRecord<String, ByteArray>, direction: Direction) {
        val consumer = when (direction) {
            Direction.IN -> inPollerConsumer
            Direction.OUT -> outPollerConsumer
        }
        val offsetToCommit = record.offset() + 1
        val offsets: Map<TopicPartition, OffsetAndMetadata> = mapOf(
            TopicPartition(record.topic(), record.partition()) to OffsetAndMetadata(offsetToCommit)
        )
        consumer.commitSync(offsets)
        logger.info("Committed offset $offsetToCommit for record with key ${record.key()}")
    }

    suspend fun sendToRetryQueueIncoming(
        record: ReceiverRecord<String, ByteArray>,
        reason: String? = null,
        nextRetryTime: String? = null
    ) {
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
            "Sending message to $topic queue with reason: $reason"
        )
        if (reason != null) {
            record.addHeader(RETRY_REASON, reason)
        }
        if (nextRetryTime != null) {
            record.addHeader(RETRY_AFTER, nextRetryTime)
        }
        try {
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
            logger.info("Offset on metadata: " + metadata.offset())
            logger.info("Result " + metadata.partition() + " timestamp " + metadata.timestamp())
            logger.info(
                "Message sent successfully to topic $topic"
            )
        } catch (e: Exception) {
            logger.info(
                "Failed to send message to $topic : ${e.message}"
            )
        }
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

fun getRecordsToConsume(
    pollerConsumer: KafkaConsumer<String, ByteArray>,
    limit: Int
): List<ConsumerRecord<String, ByteArray>> {
    // poll() returnerer det den finner innen timeout, uten garanti for at det er alle som ligger på topic.
    // Dvs den kan returnere 0-N records når det ligger N records på topic.
    // Vi ønsker å få lest ALLE opp til angitt limit.
    // Dette kan vi ikke få til på en garantert måte, men vi satser på at vi i alle fall finner 1 record hver gang vi gjør poll,
    // så poller vi helt til vi ikke får flere (har da forhåpentlig lest alle) eller til vi passerer limiten.
    var records = pollerConsumer.poll(Duration.ofMillis(500))
    val allRecords = records.toMutableList()
    while (!records.isEmpty && allRecords.count() < limit) {
        records = pollerConsumer.poll(Duration.ofMillis(200))
        allRecords.addAll(records)
    }
    return allRecords
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
