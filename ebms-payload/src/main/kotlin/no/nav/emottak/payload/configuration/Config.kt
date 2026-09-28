package no.nav.emottak.payload.configuration

import no.nav.emottak.util.KeyStoreConfiguration
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.validering.sertifikat.CertificateAuthority

data class Config(
    val caList: List<CertificateAuthority>,
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val helseId: HelseId,
    val signering: List<KeyStoreConfiguration>,
    val dekryptering: List<KeyStoreConfiguration>
)

data class HelseId(
    val nhnUrl: String,
    val openIdConfigCacheTimeInSec: Long,
    val issuerDefaultValue: String,
    val jwksUrlDefaultValue: String,
    val allowedClockSkewInMs: Long,
    val allowedMessageGenerationGapInMs: Long
)
