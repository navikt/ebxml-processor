package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbMSBaseMessage
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

abstract class Processor() {
    // TODO: vurder å ta fra RAY: processorer returnerer Events med status

    val log = LoggerFactory.getLogger(this.javaClass)
    abstract fun process()
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

    fun createEvent(status: Event.Status) = Event(
        Event.defaultProcessName(this.javaClass),
        status,
        correlationId = korrelasjonsId()
    )

    abstract fun korrelasjonsId(): String
    abstract fun persisterHendelse(event: Event): Boolean

    fun lagOgLagreHendelse(status: Event.Status){
        persisterHendelse(
            createEvent(status)
        )
    }

}