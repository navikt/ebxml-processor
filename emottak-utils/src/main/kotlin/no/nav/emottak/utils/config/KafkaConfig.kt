package no.nav.emottak.utils.config

import com.sksamuel.hoplite.Masked
import io.github.nomisRev.kafka.publisher.PublisherSettings
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

data class Kafka(
    val bootstrapServers: String,
    val securityProtocol: SecurityProtocol,
    val keystoreType: KeystoreType,
    val keystoreLocation: KeystoreLocation,
    val keystorePassword: Masked,
    val truststoreType: TruststoreType,
    val truststoreLocation: TruststoreLocation,
    val truststorePassword: Masked,
    val groupId: String,
    val topic: String,
    val eventLoggingProducerActive: Boolean
)

private fun Kafka.toProperties() = Properties()
    .apply {
        put(SECURITY_PROTOCOL_CONFIG, securityProtocol.value)
        put(SSL_KEYSTORE_TYPE_CONFIG, keystoreType.value)
        put(SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation.value)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword.value)
        put(SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType.value)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation.value)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword.value)
    }

fun Kafka.toKafkaPublisherSettings(): PublisherSettings<String, ByteArray> =
    PublisherSettings(
        bootstrapServers = bootstrapServers,
        keySerializer = StringSerializer(),
        valueSerializer = ByteArraySerializer(),
        properties = toProperties()
    )

@JvmInline
value class SecurityProtocol(val value: String)

@JvmInline
value class KeystoreType(val value: String)

@JvmInline
value class KeystoreLocation(val value: String)

@JvmInline
value class TruststoreType(val value: String)

@JvmInline
value class TruststoreLocation(val value: String)

const val SECURITY_PROTOCOL_CONFIG = "security.protocol"
const val SSL_KEYSTORE_TYPE_CONFIG = "ssl.keystore.type"
const val SSL_KEYSTORE_LOCATION_CONFIG = "ssl.keystore.location"
const val SSL_KEYSTORE_PASSWORD_CONFIG = "ssl.keystore.password"
const val SSL_TRUSTSTORE_TYPE_CONFIG = "ssl.truststore.type"
const val SSL_TRUSTSTORE_LOCATION_CONFIG = "ssl.truststore.location"
const val SSL_TRUSTSTORE_PASSWORD_CONFIG = "ssl.truststore.password"
