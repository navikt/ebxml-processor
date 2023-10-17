package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error

abstract class EbMSMessageProcessor(val ebMSMessage: EbMSMessage): Processor() {

    override fun korrelasjonsId(): String { //TODO PLACEHOLDER for correlationId
        return ebMSMessage.messageHeader.conversationId + "_" + ebMSMessage.messageHeader.messageData.messageId // TODO placeholder
    }
    
    override fun persisterHendelse(event: Event): Boolean {
        ebMSMessage.addHendelse(event)
        log.info(this.ebMSMessage.messageHeader.marker(), "Hendelse persistert: $event")
        return true; // TODO publiser hendelse
    }

    open class EbxmlProcessException(message: String, val ebxmlError: Error): RuntimeException(message) {

    }
}