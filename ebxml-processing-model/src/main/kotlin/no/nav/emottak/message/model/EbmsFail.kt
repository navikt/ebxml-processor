package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.xml.xmlMarshaller
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.ObjectFactory

data class EbmsFail(
    override val requestId: String,
    override val messageId: String,
    override val refToMessageId: String?,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val feil: List<Feil>,
    override val dokument: Document? = null

) : EbmsMessage() {

    override fun toEbmsDokument(): EbMSDocument {
        val header = this.createMessageHeader(this.addressing.copy(action = EbXMLConstants.MESSAGE_ERROR_ACTION, service = EbXMLConstants.EBMS_SERVICE_URI))
        return ObjectFactory().createEnvelope()!!.also {
            it.header = header.also {
                it.any.add(this.feil.asErrorList())
            }
            it.body = Body()
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            EbMSDocument(requestId, it, emptyList())
        }
    }
}
