package no.nav.emottak.ebms.kafka

import no.nav.emottak.ebms.log
import no.nav.emottak.util.getEnvVar
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

class KafkaClient {

    val cluster = getEnvVar("NAIS_CLUSTER_NAME", "local")

    private val kafkaBrokers = getEnvVar("KAFKA_BROKERS", "http://localhost:9092")
    private val keystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH", "")
    private val keystorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD", "")
    private val truststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH", "")
    private val truststorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD", "")

    fun createProducer(): KafkaProducer<String, String> {
        log.debug("Kafka brokers: $kafkaBrokers")
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)

            // Authentication
            if (cluster in setOf("dev-fss", "prod-fss")) {
                put("security.protocol", "SSL")
                put("ssl.keystore.type", "PKCS12")
                put("ssl.keystore.location", keystoreLocation)
                put("ssl.keystore.password", keystorePassword)
                put("ssl.truststore.type", "JKS")
                put("ssl.truststore.location", truststoreLocation)
                put("ssl.truststore.password", truststorePassword)
            }

            // Performance
            put(ProducerConfig.BUFFER_MEMORY_CONFIG, "16777216")
            put(ProducerConfig.BATCH_SIZE_CONFIG, "8192")
            put(ProducerConfig.RETRIES_CONFIG, "3")
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000")
        }
        return KafkaProducer(props)
    }

    fun createConsumer(): KafkaConsumer<String, String> {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "ebms-provider")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            // put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

            // Authentication
            if (cluster in setOf("dev-fss", "prod-fss")) {
                put("security.protocol", "SSL")
                put("ssl.keystore.type", "PKCS12")
                put("ssl.keystore.location", keystoreLocation)
                put("ssl.keystore.password", keystorePassword)
                put("ssl.truststore.type", "JKS")
                put("ssl.truststore.location", truststoreLocation)
                put("ssl.truststore.password", truststorePassword)
            }

            // Performance
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
            put(ProducerConfig.BUFFER_MEMORY_CONFIG, "16777216")
            put(ProducerConfig.BATCH_SIZE_CONFIG, "8192")
            put(ProducerConfig.RETRIES_CONFIG, "3")
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000")
        }
        return KafkaConsumer(props)
    }
}
