package no.nav.emottak.utils.kafka

import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.config.toProperties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

class KafkaPublisherClient(val topic: String, val config: Kafka) : AutoCloseable {
    // private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private var producer: KafkaProducer<String, ByteArray>? = null
    }

    suspend fun send(key: String, value: ByteArray) {
        try {
            getProducer().send(
                ProducerRecord(topic, key, value)
            )
            getProducer().flush()
            // log.debug("Message ($key) sent successfully to topic ($topic)")
        } catch (e: Exception) {
            // log.error("Exception while writing message ($key) to queue ($topic)", e)
        }
    }

    private fun getProducer(): KafkaProducer<String, ByteArray> {
        if (producer == null) {
            producer = createPublisher()
        }
        return producer!!
    }

    private fun createPublisher(): KafkaProducer<String, ByteArray> {
        val properties = config.toProperties().apply {
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer())
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer())
            put(ProducerConfig.ACKS_CONFIG, "all")
            // Performance
            put(ProducerConfig.BUFFER_MEMORY_CONFIG, "16777216")
            put(ProducerConfig.BATCH_SIZE_CONFIG, "8192")
            put(ProducerConfig.RETRIES_CONFIG, "3")
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000")
        }
        return KafkaProducer(properties)
    }

    override fun close() {
        try {
            getProducer().close()
        } catch (_: Exception) {}
    }
}
