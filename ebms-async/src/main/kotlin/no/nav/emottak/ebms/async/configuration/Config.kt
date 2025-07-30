package no.nav.emottak.ebms.async.configuration

import no.nav.emottak.config.KafkaPayloadProducer
import no.nav.emottak.config.KafkaPayloadReceiver
import no.nav.emottak.config.KafkaSignalProducer
import no.nav.emottak.config.KafkaSignalReceiver
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.environment.getEnvVar
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG
import java.util.Properties

data class Config(
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val kafkaSignalReceiver: KafkaSignalReceiver,
    val kafkaSignalProducer: KafkaSignalProducer,
    val kafkaPayloadReceiver: KafkaPayloadReceiver,
    val kafkaPayloadProducer: KafkaPayloadProducer,
    val kafkaErrorQueue: KafkaErrorQueue
)

data class KafkaErrorQueue(
    val active: Boolean,
    val topic: String
)

fun Kafka.toProperties() = Properties()
    .apply {
        put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "local") {
            put(SECURITY_PROTOCOL_CONFIG, securityProtocol.value)
            put(SSL_KEYSTORE_TYPE_CONFIG, keystoreType.value)
            put(SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation.value)
            put(SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword.value)
            put(SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType.value)
            put(SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation.value)
            put(SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword.value)
        }
    }
