package no.nav.emottak.ebms.model

import no.nav.emottak.constants.EbXMLConstants
import no.nav.emottak.ebms.ebxml.createResponseHeader
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.asErrorList
import no.nav.emottak.util.marker
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Header
import org.xmlsoap.schemas.soap.envelope.ObjectFactory
import java.util.UUID
import no.nav.emottak.ebms.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

class EbmsFail(
    requestId: String,
    messageId: String,
    override val refToMessageId: String,
    conversationId: String,
    cpaId: String,
    addressing: Addressing,
    val feil: List<Feil>,
    document: Document? = null
) : EbmsMessage(
    requestId,
    messageId,
    conversationId,
    cpaId,
    addressing,
    document,
    refToMessageId
) {

    override fun toEbmsDokument(): EbMSDocument {
        val messageHeader = this.createMessageHeader( this.addressing.copy(action = EbXMLConstants.MESSAGE_ERROR_ACTION, service = EbXMLConstants.EBMS_SERVICE_URI))
        val header = MessageHeader().marker()
        log.warn(this.marker(), "Oppretter ErrorList")
        return ObjectFactory().createEnvelope()!!.also {
            it.header = Header().also {
                it.any.add(messageHeader)
                it.any.add(this.feil.asErrorList())
            }
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            no.nav.emottak.ebms.model.log.info(this.marker(), "Signerer ErrorList (TODO)")
            // @TODO   val signatureDetails = runBlocking {  getPublicSigningDetails(this@EbMSMessageError.messageHeader) }
            EbMSDocument(UUID.randomUUID().toString(), it, emptyList())
            // @TODO      .signer(signatureDetails)
        }
    }
}
