package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.async.configuration.Kafka
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.processing.SignalProcessor
import no.nav.emottak.ebms.log
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
            autoOffsetReset = AutoOffsetReset.Latest,
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
