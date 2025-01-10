package no.nav.emottak.ebms.messaging

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.configuration.Kafka
import no.nav.emottak.ebms.configuration.toProperties
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.processing.SignalProcessor
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

suspend fun startSignalReceiver(kafka: Kafka) {
    log.info("Starting signal message receiver on topic ${kafka.incomingSignalTopic}")
    val receiverSettings: ReceiverSettings<String, ByteArray> =
        ReceiverSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = kafka.groupId,
            pollTimeout = 10.seconds,
            properties = kafka.toProperties()
        )

    val signalProcessor = SignalProcessor()
    KafkaReceiver(receiverSettings)
        .receive(kafka.incomingSignalTopic)
        .map { record ->
            signalProcessor.processSignal(record.key(), record.value())
            record.offset.acknowledge()
        }.collect()
}
