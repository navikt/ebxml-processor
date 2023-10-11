package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbMSBaseMessage
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

abstract class Processor(open val ebMSMessage: EbMSBaseMessage) {
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
            throw t;
        }
    }

    fun lagOgLagreHendelse(status: Event.Status){
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
        log.info(this.ebMSMessage.messageHeader.marker(), "Hendelse persistert: $event")
        return true; // TODO publiser hendelse
    }
    
}