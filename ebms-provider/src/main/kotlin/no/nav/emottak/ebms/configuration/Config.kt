package no.nav.emottak.ebms.configuration

import com.sksamuel.hoplite.Masked
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG
import java.util.Properties

data class Config(
    val kafka: Kafka
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

data class Kafka(
    val bootstrapServers: String,
    val securityProtocol: SecurityProtocol,
    val keystoreType: KeystoreType,
    val keystoreLocation: KeystoreLocation,
    val keystorePassword: Masked,
    val truststoreType: TruststoreType,
    val truststoreLocation: TruststoreLocation,
    val truststorePassword: Masked,
    val incomingPayloadTopic: String,
    val incomingSignalTopic: String,
    val groupId: String
)

fun Kafka.toProperties() = Properties()
    .apply {
        put(SECURITY_PROTOCOL_CONFIG, securityProtocol.value)
        put(SSL_KEYSTORE_TYPE_CONFIG, keystoreType.value)
        put(SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation.value)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword.value)
        put(SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType.value)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation.value)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword.value)
        put("max.poll.records", 10)
    }