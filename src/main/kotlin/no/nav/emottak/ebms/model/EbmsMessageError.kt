package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.getPublicSigningDetails
import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import no.nav.emottak.ebms.processing.SignaturValidator
import no.nav.emottak.ebms.processing.signer
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
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
                SertifikatsjekkProcessor(this)
            )
                .forEach { it.processWithEvents() }
            val signatureDetails = getPublicSigningDetails(messageHeader)
            SignaturValidator().validate(signatureDetails, this.dokument!!, emptyList())
        }catch (ex: Exception) {
            return
        }
    }

    fun toEbmsDokument(): EbMSDocument {
        log.warn(this.messageHeader.marker(), "Oppretter ErrorList")
        return ObjectFactory().createEnvelope()!!.also {
            it.header = Header().also {
                it.any.add(this.messageHeader)
                it.any.add(this.errorList)
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            log.info(this.messageHeader.marker(), "Signerer ErrorList (TODO)")
            val signatureDetails = getPublicSigningDetails(this.messageHeader)
            EbMSDocument("contentID",it, emptyList()).signer(signatureDetails)
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
