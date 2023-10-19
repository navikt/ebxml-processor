package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbMSBaseMessage
import no.nav.emottak.util.marker

abstract class EbMSMessageProcessor(val ebMSMessage: EbMSBaseMessage): Processor() {

    override fun korrelasjonsId(): String { //TODO PLACEHOLDER for correlationId
        return ebMSMessage.messageHeader.conversationId + "_" + ebMSMessage.messageHeader.messageData.messageId // TODO placeholder
    }

    override fun persisterHendelse(event: Event): Boolean {
        // Vi vil se på det ebMSMessage.addHendelse(event)
        when (event.eventStatus) {
            Event.Status.STARTED -> log.info(this.ebMSMessage.messageHeader.marker(), "$event")
            Event.Status.OK -> log.info(this.ebMSMessage.messageHeader.marker(), "$event")
            Event.Status.FAILED -> log.error(this.ebMSMessage.messageHeader.marker(), "$event")
        }
        return true; // TODO publiser hendelse
    }
}