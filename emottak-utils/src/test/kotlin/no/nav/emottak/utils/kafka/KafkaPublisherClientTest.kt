package no.nav.emottak.utils.kafka

import com.sksamuel.hoplite.Masked
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.config.KeystoreLocation
import no.nav.emottak.utils.config.KeystoreType
import no.nav.emottak.utils.config.SecurityProtocol
import no.nav.emottak.utils.config.TruststoreLocation
import no.nav.emottak.utils.config.TruststoreType
import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventType
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import java.util.Properties
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Execution(ExecutionMode.SAME_THREAD)
class KafkaPublisherClientTest {

    @Test
    fun `Legg 2 meldinger på Kafka`() {
        kafkaConsumer.subscribe(listOf(TOPIC))
        runBlocking {
            kafkaPublisher.publishMessage(randomEvent("Event 1").toByteArray())
            kafkaPublisher.publishMessage(randomEvent("Event 2").toByteArray())
        }
        val msgs: List<ConsumerRecord<String, ByteArray>> = readRecentMessages()
        Assertions.assertEquals(2, msgs.size)

        val firstEventJson = msgs.first().value().decodeToString()
        val firstEvent = Json.decodeFromString<Event>(firstEventJson)
        Assertions.assertEquals("Event 1", firstEvent.eventData)

        val lastEventJson = msgs.last().value().decodeToString()
        val lastEvent = Json.decodeFromString<Event>(lastEventJson)
        Assertions.assertEquals("Event 2", lastEvent.eventData)
    }

    @Test
    fun `Legg 1 melding på Kafka`() {
        kafkaConsumer.subscribe(listOf(TOPIC))
        runBlocking {
            kafkaPublisher.publishMessage(randomEvent("Ny event 3").toByteArray())
        }
        val msgs: List<ConsumerRecord<String, ByteArray>> = readRecentMessages()
        Assertions.assertEquals(1, msgs.size)

        val firstEventJson = msgs.first().value().decodeToString()
        val firstEvent = Json.decodeFromString<Event>(firstEventJson)
        Assertions.assertEquals("Ny event 3", firstEvent.eventData)
    }

    private fun randomEvent(eventData: String? = null) = Event(
        eventType = EventType.entries.toTypedArray().random(),
        requestId = Uuid.random(),
        contentId = "contentId",
        messageId = "messageId",
        eventData = eventData
    )

    private fun readRecentMessages(): List<ConsumerRecord<String, ByteArray>> {
        try {
            return kafkaConsumer.poll(Duration.ofMillis(500)).toList()
        } catch (e: Exception) {
            println("Feilet med å lese fra Kafka-kø: $e")
            return emptyList()
        } finally {
            kafkaConsumer.commitSync()
            kafkaConsumer.unsubscribe()
        }
    }

    companion object {
        private const val TOPIC = "emottak.test.topic"
        lateinit var kafkaPublisher: KafkaPublisherClient
        lateinit var kafkaConsumer: KafkaConsumer<String, ByteArray>

        @JvmStatic
        @BeforeAll
        fun setup() {
            println("=== Kafka Test Container ===")
            KafkaTestContainer.start()
            println("KafkaTestContainer.bootstrapServers: ${KafkaTestContainer.bootstrapServers}")
            kafkaPublisher = KafkaPublisherClient(kafkaSettings(KafkaTestContainer.bootstrapServers))
            kafkaConsumer = createConsumer(KafkaTestContainer.bootstrapServers)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            KafkaTestContainer.stop()
            kafkaConsumer.close()
        }

        private fun kafkaSettings(bootstrapServers: String) = Kafka(
            bootstrapServers,
            securityProtocol = SecurityProtocol("PLAINTEXT"),
            keystoreType = KeystoreType(""),
            keystoreLocation = KeystoreLocation(""),
            keystorePassword = Masked(""),
            truststoreType = TruststoreType(""),
            truststoreLocation = TruststoreLocation(""),
            truststorePassword = Masked(""),
            groupId = "ebms-provider",
            topic = TOPIC,
            eventLoggingProducerActive = false
        )

        private fun createConsumer(bootstrapServers: String): KafkaConsumer<String, ByteArray> {
            return KafkaConsumer(
                Properties().apply {
                    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                    put(ConsumerConfig.GROUP_ID_CONFIG, "ebms-provider")
                    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
                    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                    put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
                }
            )
        }
    }
}
