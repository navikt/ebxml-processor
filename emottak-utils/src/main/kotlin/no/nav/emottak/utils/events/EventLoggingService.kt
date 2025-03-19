package no.nav.emottak.utils.events

import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventMessageDetails
import no.nav.emottak.utils.kafka.KafkaPublisherClient
import org.apache.kafka.clients.producer.RecordMetadata

class EventLoggingService(
    private val kafkaPublisherClient: KafkaPublisherClient
) {

    private suspend fun logEvent(event: Event): Result<RecordMetadata> =
        kafkaPublisherClient.publishMessage(event.toByteArray())

    private suspend fun logEventMessageDetails(eventMessageDetails: EventMessageDetails): Result<RecordMetadata> =
        kafkaPublisherClient.publishMessage(eventMessageDetails.toByteArray())
}
