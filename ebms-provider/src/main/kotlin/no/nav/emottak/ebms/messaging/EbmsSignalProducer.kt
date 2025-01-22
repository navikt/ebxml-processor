package no.nav.emottak.ebms.messaging

import io.github.nomisRev.kafka.Acks
import io.github.nomisRev.kafka.KafkaProducer
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.kafkaProducer
import kotlinx.coroutines.flow.Flow
import no.nav.emottak.ebms.configuration.Kafka
import no.nav.emottak.ebms.configuration.toProperties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer

class EbmsSignalProducer(val topic: String, kafka: Kafka) {
    private var producersFlow: Flow<KafkaProducer<String, ByteArray>>

    init {
        val producerSettings = ProducerSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringSerializer(),
            valueDeserializer = ByteArraySerializer(),
            acks = Acks.All,
            other = kafka.toProperties()
        )
        producersFlow = kafkaProducer(producerSettings)
    }

    suspend fun send(key: String, value: ByteArray) {
        try {
            producersFlow.collect { producer ->
                val record = ProducerRecord(topic, key, value)
                producer.send(record).get()
            }
            println("Kafka test: Message sent successfully to topic $topic")
        } catch (e: Exception) {
            println("Kafka test: Failed to send message: ${e.message}")
        }
    }
}
