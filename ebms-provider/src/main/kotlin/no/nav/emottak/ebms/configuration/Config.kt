package no.nav.emottak.ebms.configuration

import no.nav.emottak.config.KafkaPayloadProducer
import no.nav.emottak.config.KafkaPayloadReceiver
import no.nav.emottak.config.KafkaSignalProducer
import no.nav.emottak.config.KafkaSignalReceiver
import no.nav.emottak.util.KeyStoreConfiguration
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka

data class Config(
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val kafkaSignalReceiver: KafkaSignalReceiver,
    val kafkaSignalProducer: KafkaSignalProducer,
    val kafkaPayloadReceiver: KafkaPayloadReceiver,
    val kafkaPayloadProducer: KafkaPayloadProducer,
    val signering: List<KeyStoreConfiguration>
)
