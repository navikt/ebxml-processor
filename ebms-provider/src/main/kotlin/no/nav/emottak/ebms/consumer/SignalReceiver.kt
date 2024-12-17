package no.nav.emottak.ebms.consumer

import arrow.resilience.Schedule
import io.github.nomisRev.kafka.map
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import no.nav.emottak.ebms.configuration.Kafka
import no.nav.emottak.ebms.configuration.toProperties
import no.nav.emottak.ebms.log
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SignalReceiver(
    private val kafkaConfig: Kafka
) {
    private val settings: ReceiverSettings<Reference, Content> =
        ReceiverSettings(
            bootstrapServers = kafkaConfig.bootstrapServers,
            keyDeserializer = StringDeserializer().map(::Reference),
            valueDeserializer = ByteArrayDeserializer().map(::Content),
            groupId = kafkaConfig.groupId,
            autoOffsetReset = AutoOffsetReset.Earliest, // TODO set this to something else
            properties = kafkaConfig.toProperties()
        )

    suspend fun schedule(interval: Duration = 30.seconds) =
        Schedule
            .spaced<Unit>(interval)
            .repeat(this::processMessages)

    private suspend fun processMessages() =
        KafkaReceiver(settings)
            .receive(kafkaConfig.incomingSignalTopic)
            .take(10)
            .map { it.key() to it.value() }
            .collect(::processSignal)

    private fun processSignal(signal: Pair<Reference, Content>) {
        log.info("Got signal with reference <${signal.first.value}> and content: ${String(signal.second.value)}")
    }
}

@JvmInline
value class Reference(val value: String)

@JvmInline
value class Content(val value: ByteArray)
