package no.nav.emottak.ebms.async.processing

import kotlinx.serialization.json.Json
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.log
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.toKafkaHeaders
import no.nav.emottak.ebms.util.toByteArray
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.util.marker
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType

suspend fun sendSignalResponseToTopic(
    ebmsSignalProducer: EbmsMessageProducer,
    eventRegistrationService: EventRegistrationService,
    ebmsDocument: EbmsDocument,
    signalResponderEmails: List<EmailAddress>
) {
    if (config().kafkaSignalProducer.active) {
        val messageHeader = ebmsDocument.messageHeader()
        try {
            log.info(messageHeader.marker(), "Sending message to Kafka queue")
            eventRegistrationService.runWithEvent(
                successEvent = EventType.MESSAGE_PLACED_IN_QUEUE,
                failEvent = EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                requestId = ebmsDocument.requestId.parseOrGenerateUuid(),
                messageId = ebmsDocument.messageHeader().messageData.messageId ?: "",
                eventData = Json.encodeToString(
                    mapOf(EventDataType.QUEUE_NAME.value to config().kafkaSignalProducer.topic)
                )
            ) {
                ebmsSignalProducer.publishMessage(
                    key = ebmsDocument.requestId,
                    value = ebmsDocument.document.toByteArray(),
                    headers = signalResponderEmails.toKafkaHeaders() + messageHeader.toKafkaHeaders()
                )
            }
        } catch (e: Exception) {
            log.error(messageHeader.marker(), "Exception occurred while sending message to Kafka queue", e)
        }
    }
}
