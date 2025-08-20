package no.nav.emottak.config

/* Config-modeller som deles mellom:
    - ebms-provider
    - ebms-async
 */

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
