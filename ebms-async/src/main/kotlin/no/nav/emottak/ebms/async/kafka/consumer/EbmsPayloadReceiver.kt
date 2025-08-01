package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.processing.PayloadMessageService
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

suspend fun startPayloadReceiver(
    topic: String,
    kafka: Kafka,
    payloadMessageService: PayloadMessageService
) {
    log.info("Starting payload message receiver on topic $topic")
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
            payloadMessageService.process(record)
            record.offset.acknowledge()
        }.collect()
}
