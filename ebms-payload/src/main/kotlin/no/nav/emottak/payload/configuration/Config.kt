package no.nav.emottak.payload.configuration

import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka

data class CertificateAuthority(
    val dn: String,
    val ocspUrl: String
)

data class Config(
    val caList: List<CertificateAuthority>,
    val kafka: Kafka,
    val eventLogging: EventLogging
)
