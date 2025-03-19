package no.nav.emottak.utils.events

import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventMessageDetails
import no.nav.emottak.utils.kafka.KafkaPublisherClient

class EventLoggingService(
    private val kafkaPublisherClient: KafkaPublisherClient
) {

    private suspend fun logEvent(event: Event) {
        kafkaPublisherClient.publishMessage(event.toByteArray())
    }

    private suspend fun logEventMessageDetails(eventMessageDetails: EventMessageDetails) =
        kafkaPublisherClient.publishMessage(eventMessageDetails.toByteArray())
}
