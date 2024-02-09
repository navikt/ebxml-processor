package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbmsBaseMessage
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

abstract class Processor(open val ebMSMessage: EbmsBaseMessage) {
    // TODO: vurder å ta fra RAY: processorer returnerer Events med status

    val log = LoggerFactory.getLogger(this.javaClass)
    abstract fun process() // TODO kan sikkert ta imot en context. EbmsMessageContext?
    fun processWithEvents() {
        lagOgLagreHendelse(Event.Status.STARTED)
        try {
            process()
            lagOgLagreHendelse(Event.Status.OK)
        } catch (t: Throwable) {
            lagOgLagreHendelse(Event.Status.FAILED)
            throw t
        }
    }

    fun lagOgLagreHendelse(status: Event.Status) {
        persisterHendelse(
            Event(
                this.javaClass.simpleName,
                status,
                correlationId = ebMSMessage.messageHeader.conversationId + ebMSMessage.messageHeader.messageData.messageId // TODO placeholder
            )
        )
    }

    fun persisterHendelse(event: Event): Boolean {
        // Vi vil se på det ebMSMessage.addHendelse(event)
        when (event.eventStatus) {
            Event.Status.STARTED -> log.debug(this.ebMSMessage.messageHeader.marker(), "{}", event)
            Event.Status.OK -> log.info(this.ebMSMessage.messageHeader.marker(), "$event")
            Event.Status.FAILED -> log.error(this.ebMSMessage.messageHeader.marker(), "$event")
        }
        return true; // TODO publiser hendelse
    }
}
