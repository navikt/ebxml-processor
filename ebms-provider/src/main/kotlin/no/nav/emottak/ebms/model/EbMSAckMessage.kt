package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.getPublicSigningDetails
import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import no.nav.emottak.ebms.processing.SignaturValidator
import no.nav.emottak.ebms.processing.signer
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory

class EbMSAckMessage(override val messageHeader: MessageHeader,
                     val acknowledgment: Acknowledgment,
                     override val dokument: Document? = null) : EbMSBaseMessage {

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
            val signatureDetails = getPublicSigningDetails(this.messageHeader)
            EbMSDocument("contentID",it, emptyList()).signer(signatureDetails)
        }
    }

}