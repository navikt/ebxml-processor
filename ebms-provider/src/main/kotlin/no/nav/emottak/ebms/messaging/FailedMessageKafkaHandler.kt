package no.nav.emottak.ebms.messaging

import io.github.nomisRev.kafka.Acks
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.kafkaProducer
import io.github.nomisRev.kafka.receiver.KafkaReceiver
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
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds

val failedMessageQueue: FailedMessageKafkaHandler = FailedMessageKafkaHandler()
const val RETRY_COUNT_HEADER = "retryCount"
const val RETRY_AFTER = "retryableAfter"

class FailedMessageKafkaHandler(
    val kafkaErrorQueue: KafkaErrorQueue = config().kafkaErrorQueue,
    kafka: Kafka = config().kafka
) {
    val logger = LoggerFactory.getLogger(FailedMessageKafkaHandler::class.java)
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

    suspend fun send(key: String, value: ByteArray, record: ReceiverRecord<String, ByteArray>) {
        record.addHeader(RETRY_AFTER, getNextRetryTime(record)) // TODO add retry logic
        try {
            producersFlow.collect { producer ->
                producer.send(ProducerRecord(kafkaErrorQueue.topic, null, key, value, record.headers())).get()
            }
            println("Kafka test: Message sent successfully to topic ${kafkaErrorQueue.topic}")
        } catch (e: Exception) {
            println("Kafka test: Failed to send message: ${e.message}")
        }
    }

    suspend fun receive(payloadMessageProcessor: PayloadMessageProcessor) {
        consumerFlow.map { record ->
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
