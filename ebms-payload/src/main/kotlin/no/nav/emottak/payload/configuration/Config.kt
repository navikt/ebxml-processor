package no.nav.emottak.payload.configuration

import no.nav.emottak.util.KeyStoreConfiguration
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka

data class Config(
    val caList: List<CertificateAuthority>,
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val helseId: HelseId,
    val signering: List<KeyStoreConfiguration>,
    val dekryptering: List<KeyStoreConfiguration>
)

data class CertificateAuthority(
    val dn: String,
    val ocspUrl: String
)

data class HelseId(
    val issuerUrl: String,
    val jwksUrl: String,
    val allowedClockSkewInMs: Long,
    val allowedMessageGenerationGapInMs: Long
)
