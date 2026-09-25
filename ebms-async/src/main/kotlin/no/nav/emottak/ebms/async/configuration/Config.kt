package no.nav.emottak.ebms.async.configuration

import no.nav.emottak.util.KeyStoreConfiguration
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.config.toProperties
import no.nav.emottak.utils.environment.getEnvVar
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG
import java.time.LocalTime
import kotlin.time.Duration

data class Config(
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val kafkaSignalReceiver: KafkaSignalReceiver,
    val kafkaSignalProducer: KafkaSignalProducer,
    val kafkaPayloadReceiver: KafkaPayloadReceiver,
    val kafkaPayloadProducer: KafkaPayloadProducer,
    val kafkaEbmsInPayloadProducer: KafkaEbmsInPayloadProducer,
    val kafkaErrorQueue: KafkaErrorQueue,
    val kafkaErrorQueueOut: KafkaErrorQueueOut,
    val kafkaEbmsOutPayloadReceiver: KafkaEbmsOutPayloadReceiver,
    val signering: List<KeyStoreConfiguration>,
    val errorRetryPolicyIncoming: ErrorRetryPolicy,
    val errorRetryPolicyOutgoing: ErrorRetryPolicy,
    val messageResendPolicy: MessageResendPolicy,
    val cleanupPayloadsJob: CleanupPayloadsJob
)

data class MessageResendPolicy(
    val startupDelay: Duration,
    val processInterval: Duration, // Reading/processing starts every X seconds
    val resendInterval: Duration, // Minutes to wait for Ack before resending
    val maxResends: Int // Max number of resends
)

data class ErrorRetryPolicy(
    val startupDelay: Duration,
    val processInterval: Duration,
    val maxMessagesToProcess: Int,
    val retryIntervals: List<Duration>,
    val retriesPerInterval: List<Int>,
    val maxRetries: Int
    // If retriesPerInterval is e.g. [2, 2, 1, 3, 6] and retryIntervals is [30m, 1h, 3h, 6h, 12h],
    // then the first 2 retries occur 30/60 minutes after first failure, the next 2 retries 2/3 hours after first failure,
    // the next retry occurs 6 hours after first failure, the next 3 retries 12/18/24 hours after first failure,
    // and the final 6 retries occur every 12 hours after that (36/48/60/72/84/96 hours after first failure).
) {
    fun nextInterval(retriesPerformed: Int): Duration {
        var intervalIndex = findIntervalIndex(retriesPerformed)
        if (intervalIndex > retryIntervals.lastIndex) {
            intervalIndex = retryIntervals.lastIndex
        }
        return retryIntervals[intervalIndex]
    }

    private fun findIntervalIndex(retriesPerformed: Int): Int {
        var i = 0
        var limit = 0
        while (i < retriesPerInterval.size) {
            limit = limit + retriesPerInterval[i]
            if (retriesPerformed < limit) return i
            i++
        }
        return retryIntervals.size
    }
}

data class CleanupPayloadsJob(
    val enabled: Boolean,
    val fixedInterval: Duration,
    val startAtTime: StartAtTime,
    val keepPayloadsDays: KeepDays,
    val batchSize: BatchSize
)

@JvmInline
value class StartAtTime(val value: LocalTime)

@JvmInline
value class KeepDays(val value: Int)

@JvmInline
value class BatchSize(val value: Long)

fun Kafka.toProperties() =
    toProperties()
        .apply {
            put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            if (getEnvVar("NAIS_CLUSTER_NAME", "local") == "local") {
                remove(SECURITY_PROTOCOL_CONFIG, securityProtocol.value)
                remove(SSL_KEYSTORE_TYPE_CONFIG, keystoreType.value)
                remove(SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation.value)
                remove(SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword.value)
                remove(SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType.value)
                remove(SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation.value)
                remove(SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword.value)
            }
        }
