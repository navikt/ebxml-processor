package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import no.nav.emottak.ebms.processing.SignatursjekkProcessor
import no.nav.emottak.ebms.xml.xmlMarshaller
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory

class EbmsAcknowledgment(override val messageHeader: MessageHeader,
                         val acknowledgment: Acknowledgment,
                         override val dokument: Document? = null) : EbMSBaseMessage {



    fun process() {
        try {
            listOf(
                CPAValidationProcessor(this),
                SertifikatsjekkProcessor(this),
                SignatursjekkProcessor(dokument!!, this)
            )
                .forEach { it.processWithEvents() }

        }catch (ex: Exception) {
            return
        }
    }

    fun toEbmsDokument(): EbMSDocument {
        return ObjectFactory().createEnvelope()!!.also {
            it.header = Header().also {
                it.any.add(this.messageHeader)
                it.any.add(this.acknowledgment)
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            EbMSDocument("contentID",it, emptyList())
        }
    }

}