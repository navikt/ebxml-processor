package no.nav.emottak.ebms.messaging

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.log
import no.nav.emottak.ebms.processing.SignalProcessor
import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.config.toProperties
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

suspend fun startSignalReceiver(topic: String, kafka: Kafka, signalProcessor: SignalProcessor) {
    log.info("Starting signal message receiver on topic $topic")
    val receiverSettings: ReceiverSettings<String, ByteArray> =
        ReceiverSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = kafka.groupId,
            pollTimeout = 10.seconds,
            properties = kafka.toProperties()
        )

    KafkaReceiver(receiverSettings)
        .receive(topic)
        .map { record ->
            signalProcessor.processSignal(record.key(), record.value())
            record.offset.acknowledge()
        }.collect()
}
