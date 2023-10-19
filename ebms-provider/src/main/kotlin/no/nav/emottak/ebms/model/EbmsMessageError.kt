package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.processing.signer
import no.nav.emottak.ebms.xml.xmlMarshaller
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory


class EbMSErrorMessage(
    override val messageHeader: MessageHeader,
    var errorList: ErrorList,
    override val dokument: Document? = null
) : EbMSBaseMessage {

    fun toEbmsDokument(): EbMSDocument {
        return ObjectFactory().createEnvelope()!!.also {
            it.header = Header().also {
                it.any.add(this.messageHeader)
                it.any.add(this.errorList)
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            EbMSDocument("contentID",it, emptyList()).signer(this.messageHeader)
        }
    }


}


//fun toEbmsDocument():EbMSDocument {
  // legg Evnevlope
    //legg MessageHEader
    //legg ErrorList
    //legg DOM
    // signer
    //  return EbmsDokument
//}
