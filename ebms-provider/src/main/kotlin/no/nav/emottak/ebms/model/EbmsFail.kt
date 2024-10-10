package no.nav.emottak.ebms.model

import no.nav.emottak.constants.EbXMLConstants
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.asErrorList
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.ObjectFactory
import java.util.*

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
        log.warn(this.marker(), "Oppretter ErrorList")
        return ObjectFactory().createEnvelope()!!.also {
            it.header = header.also {
                it.any.add(this.feil.asErrorList())
            }
            it.body = Body()
        }.let {
            xmlMarshaller.marshal(it)
        }.let {
            // @TODO   val signatureDetails = runBlocking {  getPublicSigningDetails(this@EbMSMessageError.messageHeader) }
            EbMSDocument(UUID.randomUUID().toString(), it, emptyList())
            // @TODO      .signer(signatureDetails)
        }
    }
}
