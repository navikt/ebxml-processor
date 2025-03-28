package no.nav.emottak.ebms.async.kafka.producer

import io.github.nomisRev.kafka.publisher.Acks
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import no.nav.emottak.ebms.async.configuration.Kafka
import no.nav.emottak.ebms.async.configuration.toProperties
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

class EbmsMessageProducer(private val topic: String, kafka: Kafka) {
    private val log = LoggerFactory.getLogger("no.nav.emottak.ebms.messaging")

    private val kafkaPublisher = KafkaPublisher(
        PublisherSettings(
            bootstrapServers = kafka.bootstrapServers,
            keySerializer = StringSerializer(),
            valueSerializer = ByteArraySerializer(),
            acknowledgments = Acks.All,
            properties = kafka.toProperties()
        )
    )

    suspend fun publishMessage(key: String, value: ByteArray): Result<RecordMetadata> =
        kafkaPublisher.publishScope {
            publishCatching(toProducerRecord(topic, key, value))
        }.onSuccess {
            log.info("Message sent successfully to topic $topic with key $key")
        }.onFailure {
            log.error("Failed to send message to topic $topic with key $key", it)
        }

    private fun toProducerRecord(topic: String, key: String, content: ByteArray): ProducerRecord<String, ByteArray> =
        ProducerRecord<String, ByteArray>(topic, key, content)
}
