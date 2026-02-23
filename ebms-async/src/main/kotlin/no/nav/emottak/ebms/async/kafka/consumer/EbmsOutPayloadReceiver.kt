package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.CommitStrategy
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.processing.PayloadMessageService
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.SendInResponse
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

suspend fun startEbmsOutPayloadReceiver(
    topic: String,
    kafka: Kafka,
    payloadMessageService: PayloadMessageService
) {
    log.info("Starting EbmsOutPayload receiver on topic $topic")
    val receiverSettings: ReceiverSettings<String, ByteArray> =
        ReceiverSettings(
            bootstrapServers = kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = "${kafka.groupId}-ebms-out-payload",
            autoOffsetReset = AutoOffsetReset.Earliest,
            commitStrategy = CommitStrategy.ByTime(5.seconds),
            pollTimeout = 10.seconds,
            properties = kafka.toProperties()
        )

    KafkaReceiver(receiverSettings)
        .receive(topic)
        .map { record ->
            val sendInResponse = Json.decodeFromString<SendInResponse>(record.value().decodeToString())
            val cpaId = record.headers().lastHeader("cpaId")?.let { String(it.value()) } ?: ""
            val refToMessageId = record.headers().lastHeader("refToMessageId")?.let { String(it.value()) }
            val payloadMessage = PayloadMessage(
                requestId = sendInResponse.requestId,
                messageId = sendInResponse.messageId,
                conversationId = sendInResponse.conversationId,
                cpaId = cpaId,
                addressing = sendInResponse.addressing,
                payload = Payload(sendInResponse.payload, ContentType.Application.Xml.toString()),
                refToMessageId = refToMessageId,
                duplicateElimination = false,
                ackRequested = true
            )
            payloadMessageService.processOutboundResponse(record, payloadMessage)
            record.offset.acknowledge()
        }.collect()
}
