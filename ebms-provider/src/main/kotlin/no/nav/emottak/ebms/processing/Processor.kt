package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbMSMessage
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.slf4j.LoggerFactory
import org.xmlsoap.schemas.soap.envelope.Envelope

abstract class Processor(val ebMSMessage: EbMSMessage) {
    // TODO: vurder å ta fra RAY: processorer returnerer Events med status

    val log = LoggerFactory.getLogger(this.javaClass)
    abstract fun process() // TODO kan sikkert ta imot en context. EbmsMessageContext?
    abstract fun createEbmsErrorElement(): Error

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
            createEvent(status)
        )
    }
    private fun createEvent(status: Event.Status) = Event(
        Event.defaultProcessName(this.javaClass),
        status,
        correlationId = korrelasjonsId()
    )

    fun korrelasjonsId(): String { //TODO PLACEHOLDER for correlationId
        return ebMSMessage.messageHeader.conversationId + "_" + ebMSMessage.messageHeader.messageData.messageId // TODO placeholder
    }

    fun persisterHendelse(event: Event): Boolean {
        ebMSMessage.addHendelse(event)
        log.info("Hendelse persistert: [%s]".format(event.toString()))
        return true; // TODO publiser hendelse
    }

    open class EbxmlProcessException(message: String, val ebxmlError: Error): RuntimeException(message) {

    }
    
}