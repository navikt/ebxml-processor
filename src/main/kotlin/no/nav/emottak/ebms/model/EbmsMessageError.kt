package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory

class EbmsMessageError(
    override val messageHeader: MessageHeader,
    var errorList: ErrorList,
    override val dokument: Document? = null
) : EbmsBaseMessage {

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
            // @TODO   val signatureDetails = runBlocking {  getPublicSigningDetails(this@EbMSMessageError.messageHeader) }
            EbMSDocument("contentID", it, emptyList())
            // @TODO      .signer(signatureDetails)
        }
    }
}