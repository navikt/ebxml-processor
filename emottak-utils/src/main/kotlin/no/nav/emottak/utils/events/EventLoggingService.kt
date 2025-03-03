package no.nav.emottak.utils.events

import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventMessageDetails

class EventLoggingService {

    companion object {
        fun logEvent(event: Event) {
            // TODO: Add event on Kafka topic
            println(event)
        }

        fun logEventMessageDetails(eventMessageDetails: EventMessageDetails) {
            // TODO: Add eventMessageDetails on Kafka topic
            println(eventMessageDetails)
        }
    }
}
