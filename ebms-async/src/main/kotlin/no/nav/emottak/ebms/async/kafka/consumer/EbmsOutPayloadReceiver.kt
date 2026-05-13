package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.CommitStrategy
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.ebms.async.configuration.toProperties
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.processing.PayloadMessageService
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.SendInResponse
import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.serialization.LENIENT_JSON_PARSER
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
            autoOffsetReset = AutoOffsetReset.Latest,
            commitStrategy = CommitStrategy.ByTime(5.seconds),
            pollTimeout = 10.seconds,
            properties = kafka.toProperties()
        )

    KafkaReceiver(receiverSettings)
        .receive(topic)
        .map { record ->
            val sendInResponse = LENIENT_JSON_PARSER.decodeFromString<SendInResponse>(record.value().decodeToString())
            val payloadMessage = PayloadMessage(
                requestId = sendInResponse.requestId,
                messageId = sendInResponse.messageId,
                conversationId = sendInResponse.conversationId,
                cpaId = sendInResponse.cpaId,
                addressing = sendInResponse.addressing,
                payload = Payload(sendInResponse.payload, ContentType.Application.Xml.toString()),
                refToMessageId = sendInResponse.refToMessageId,
                duplicateElimination = true,
                ackRequested = true
            )
            payloadMessageService.processOutboundResponse(record, payloadMessage)
            record.offset.acknowledge()
        }.collect()
}
