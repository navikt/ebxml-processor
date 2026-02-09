package no.nav.emottak.ebms.async.configuration

import no.nav.emottak.config.KafkaPayloadProducer
import no.nav.emottak.config.KafkaPayloadReceiver
import no.nav.emottak.config.KafkaSignalProducer
import no.nav.emottak.config.KafkaSignalReceiver
import no.nav.emottak.util.KeyStoreConfiguration
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
    val kafkaEbmsInProducer: KafkaEbmsInProducer,
    val kafkaErrorQueue: KafkaErrorQueue,
    val kafkaEbmsSendOutReceiver: KafkaEbmsSendOutReceiver,
    val signering: List<KeyStoreConfiguration>,
    val errorRetryPolicy: ErrorRetryPolicy
)

data class KafkaEbmsInProducer(
    val active: Boolean,
    val topic: String
)

data class KafkaErrorQueue(
    val active: Boolean,
    val topic: String,
    val initOffset: String
)

data class KafkaEbmsSendOutReceiver(
    val active: Boolean,
    val topic: String
)

data class ErrorRetryPolicy(
    val processIntervalSeconds: Int,
    val maxMessagesToProcess: Int,
    val retryIntervalsMinutes: List<Int>,
    val retriesPerInterval: List<Int>
    // If retriesPerInterval is e.g. [3, 3, 23] and retryIntervalsMinutes is [5, 15, 60, 60*24],
    // then the first 3 retries occurs 5/10/15 minutes after first failure, the next 3 retries 30/45/60 minutes after first failure,
    // the next 23 retries 2-24 hours after first failure, and any retries after that will occur every 24 hours after the previous retry.
) {
    fun nextIntervalMinutes(retriesPerformed: Int): Int {
        var intervalIndex = findIntervalIndex(retriesPerformed)
        if (intervalIndex > retryIntervalsMinutes.lastIndex) {
            intervalIndex = retryIntervalsMinutes.lastIndex
        }
        return retryIntervalsMinutes[intervalIndex]
    }

    private fun findIntervalIndex(retriesPerformed: Int): Int {
        var i = 0
        var limit = 0
        while (i < retriesPerInterval.size) {
            limit = limit + retriesPerInterval[i]
            if (retriesPerformed < limit) return i
            i++
        }
        return retryIntervalsMinutes.size
    }
}

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
