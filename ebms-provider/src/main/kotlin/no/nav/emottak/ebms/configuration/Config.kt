package no.nav.emottak.ebms.configuration

import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.config.KafkaEventLoggingProducer

data class Config(
    val kafka: Kafka,
    val kafkaEventLoggingProducer: KafkaEventLoggingProducer,
    val kafkaSignalReceiver: KafkaSignalReceiver,
    val kafkaSignalProducer: KafkaSignalProducer,
    val kafkaPayloadReceiver: KafkaPayloadReceiver,
    val kafkaPayloadProducer: KafkaPayloadProducer
)

data class KafkaSignalReceiver(
    val active: Boolean,
    val topic: String
)

data class KafkaSignalProducer(
    val active: Boolean,
    val topic: String
)

data class KafkaPayloadReceiver(
    val active: Boolean,
    val topic: String
)

data class KafkaPayloadProducer(
    val active: Boolean,
    val topic: String
)
