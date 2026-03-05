package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.publisher.Acks
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
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
import kotlin.collections.map

const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"
const val RETRY_REASON = "retryReason"

// Dette flagget er i utgangspunktet satt på for å dummy-prosessere alle gamle meldinger i feilkøen ved oppstart i DEV (ca 3000)
// men kan evt brukes også i vanlig kjøring, siden det ignorerer meldinger eldre enn 1 uke
const val IGNORE_OLD_MESSAGES = false
const val AGE_DAYS_TO_IGNORE = 7L

val logger: Logger = LoggerFactory.getLogger(FailedMessageKafkaHandler::class.java)

class FailedMessageKafkaHandler(
    val kafkaErrorQueue: KafkaErrorQueue = config().kafkaErrorQueue,
    val kafka: Kafka = config().kafka,
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

    // Set up a consumer for POLLing the retry topic. NOTE that this only polls and disconnects, it is NOT listening forever like the receivers.
    // We get into troubles with committing if we use the same groupid as the receivers used to listen on the message topics.
    val groupIdForRetry = kafka.groupId + "-retry"
    val pollerConsumer = KafkaConsumer(
        getPollerProperties(kafka.toProperties(), groupIdForRetry, config().errorRetryPolicy.processInterval.inWholeSeconds),
        StringDeserializer(),
        ByteArrayDeserializer()
    ).also { c ->
        val partitions = c.partitionsFor(kafkaErrorQueue.topic).map { TopicPartition(it.topic(), it.partition()) }
        c.assign(partitions)
    }

    fun getPollerProperties(properties: Properties, groupId: String, pollIntervalSeconds: Long): Properties {
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        // Sikre at ikke Kafka anser polleren vår som "død" selv om den ikke poller så ofte som Kafka er designet for.
        // Det vil føre til uønsket rebalansering etc, og vil tydeligvis også skje med bruk av assign() istedenfor subscribe()
        val millisAllowedBetweenPolls = (pollIntervalSeconds * 1000) + 60000
        properties.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, millisAllowedBetweenPolls.toString())
        // Hvis denne har verdi "earliest" vil man prosessere ALLE meldingene på feilkøen fra tidligste offset, første gangen app'en startes
        // Med "latest" vil man regne alle meldingene på feilkøen som allerede prosessert, og fortsette prosessering når nye meldinger kommer
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaErrorQueue.initOffset)
        return properties
    }

    suspend fun sendToRetry(
        record: ReceiverRecord<String, ByteArray>,
        key: String = record.key(),
        value: ByteArray = record.value(),
        reason: String? = null,
        advanceRetryTime: Boolean = true
    ) {
        logger.info("Sending message to retry queue with reason: $reason")
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

    // Vi forutsetter at retry-køen kun har 1 partisjon, dvs offset er unikt
    suspend fun forceRetryFailedMessage(
        messageFilterService: MessageFilterService,
        offset: Long
    ) {
        logger.info("Forcing re-run of message on error queue at offset $offset")
        val record = getRetryRecord(offset)
        if (record == null) {
            logger.info("No record in error queue at offset $offset")
            return
        }
        logger.info("${record.key()} is being re-run.")
        // Antar at vi ikke teller opp i header for denne typen kjøring
        // record.asReceiverRecord().retryCounter()
        messageFilterService.filterMessage(record)
        logger.info("${record.key()} has been re-run.")
    }

    suspend fun consumeRetryQueue(
        messageFilterService: MessageFilterService,
        limit: Int = 10
    ) {
        logger.info("Checking for messages in error queue, current offset " + pollerConsumer.position(TopicPartition(kafkaErrorQueue.topic, 0)))
        val records = getRecordsToConsume(pollerConsumer, limit)
        if (records.isEmpty()) {
            logger.info("No records to process in error queue")
            return
        }
        logger.info("At least ${records.count()} records to process in error queue")

        if (records.count() > limit) {
            logger.info("Will only process $limit records, due to limit settings")
        }
        var counter = 0
        records.forEach { record ->
            counter++
            if (counter > limit) {
                logger.info("Retry queue Limit reached: $limit")
                return@forEach
            }

            logger.info("Processing record: $counter, max is $limit, key: ${record.key()}, offset: ${record.offset()}")
            val retryableAfter = parseNextRetryHeader(record)

            logger.info("Record with key ${record.key()} is retryable after $retryableAfter.")
            val offsetToCommit = record.offset() + 1
            if (IGNORE_OLD_MESSAGES && LocalDateTime.now().minusDays(AGE_DAYS_TO_IGNORE).isAfter(retryableAfter)) {
                logger.info("${record.key()} is too old, ignoring. This should only happen during DEV, when we want to process all messages in the queue.")
            } else if (LocalDateTime.now().isAfter(retryableAfter)) {
                logger.info("${record.key()} is being retried.")
                record.asReceiverRecord().retryCounter()
                messageFilterService.filterMessage(record.asReceiverRecord())
                logger.info("${record.key()} has been retried.")
            } else {
                logger.info("${record.key()} is not retryable yet.")
                sendToRetry(record.asReceiverRecord(), advanceRetryTime = false)
            }
            val offsets: Map<TopicPartition?, OffsetAndMetadata?> =
                mapOf(
                    TopicPartition(record.topic(), record.partition())
                        to OffsetAndMetadata(offsetToCommit)
                )
            pollerConsumer.commitSync(offsets)
            logger.info("Committed offset $offsetToCommit for record with key ${record.key()}")
        }
    }

    // Så lenge parseNextRetryHeader() og getNextRetryTime() er i sync mht. timestamp-formatet, skal parsing gå bra.
    // Vi har imidlertid erfart at man kan finne "gamle" meldinger på feilkø, med annet format - sikreste er å takle begge.
    private fun parseNextRetryHeader(record: ConsumerRecord<String, ByteArray>): LocalDateTime {
        val header = String(record.headers().lastHeader(RETRY_AFTER).value())
        try {
            return LocalDateTime.parse(header)
        } catch (e: Exception) {
            val instant = Instant.parse(header)
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        }
    }

    fun getNextRetryTime(record: ReceiverRecord<String, ByteArray>): String {
        val nextInterval = errorRetryPolicy.nextInterval(record.retryCount())
        return LocalDateTime.now().plusMinutes(nextInterval.inWholeMinutes).toString()
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
