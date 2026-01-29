package no.nav.emottak.ebms.async.configuration

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

data class KafkaErrorQueue(
    val active: Boolean,
    val topic: String,
    val initOffset: String
)
