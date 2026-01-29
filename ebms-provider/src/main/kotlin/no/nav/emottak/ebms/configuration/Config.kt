package no.nav.emottak.ebms.configuration

import no.nav.emottak.util.KeyStoreConfiguration
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka

data class Config(
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val signering: List<KeyStoreConfiguration>
)
