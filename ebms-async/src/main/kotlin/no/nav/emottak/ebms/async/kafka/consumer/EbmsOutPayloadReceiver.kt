package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.processing.PayloadMessageForwardingService
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.createDocument
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

suspend fun startEbmsOutPayloadReceiver(
    topic: String,
    kafka: Kafka,
    payloadMessageForwardingService: PayloadMessageForwardingService
) {
    log.info("Starting SendOut response receiver on topic $topic")
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
            runCatching {
                val document = record.value().createDocument()
                val ebmsMessage = EbmsDocument(
                    requestId = record.key(),
                    document = document,
                    attachments = emptyList()
                ).transform()

                if (ebmsMessage is PayloadMessage) {
                    payloadMessageForwardingService.processResponse(ebmsMessage)
                } else {
                    log.warn("Received message on SendOut response topic was not a PayloadMessage: ${ebmsMessage::class.simpleName}")
                }
            }.onFailure {
                log.error("Error processing SendOut response", it)
            }
            record.offset.acknowledge()
        }.collect()
}
