package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.processing.MessageFilterService
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

// TODO not sure if needed or there is an existing way
const val MESSAGE_SOURCE_HEADER = "messageSource"
const val EBMS_OUT_PAYLOAD_SOURCE = "ebms-out-payload"

suspend fun startEbmsOutPayloadReceiver(
    topic: String,
    kafka: Kafka,
    messageFilterService: MessageFilterService
) {
    log.info("Starting EbmsOutPayload receiver on topic $topic")
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
            record.addHeader(MESSAGE_SOURCE_HEADER, EBMS_OUT_PAYLOAD_SOURCE)
            messageFilterService.filterMessage(record)
            record.offset.acknowledge()
        }.collect()
}
