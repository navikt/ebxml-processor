package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory

class EbmsAcknowledgment(
    override val messageHeader: MessageHeader,
    val acknowledgment: Acknowledgment,
    override val dokument: Document? = null
) : EbmsBaseMessage {

    fun process() {
        try {
        } catch (ex: Exception) {
            return
        }
    }

    fun toEbmsDokument(): EbMSDocument {
        log.info(this.messageHeader.marker(), "Oppretter Acknowledgment")
        return ObjectFactory().createEnvelope()!!.also {
            it.header = Header().also {
                it.any.add(this.messageHeader)
                it.any.add(this.acknowledgment)
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            log.info(this.messageHeader.marker(), "Signerer Acknowledgment (TODO)")
            EbMSDocument("contentID", it, emptyList())
        }
    }
}
