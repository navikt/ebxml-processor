package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import no.nav.emottak.ebms.processing.SignatursjekkProcessor
import no.nav.emottak.ebms.xml.xmlMarshaller
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3._2000._09.xmldsig_.SignatureType
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory


class EbMSMessageError(
    override val messageHeader: MessageHeader,
    var errorList: ErrorList,
    override val dokument: Document? = null
) : EbMSBaseMessage {

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
                it.any.add(this.errorList)
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            EbMSDocument("contentID",it, emptyList())
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
