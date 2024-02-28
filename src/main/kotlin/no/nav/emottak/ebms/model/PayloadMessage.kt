package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.createResponseHeader
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.ebms.xml.marshal
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.SignatureDetails
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Manifest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Reference
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header
import java.io.StringReader
import java.util.UUID

data class PayloadMessage(
    override val requestId: String,
    override val messageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val payload: Payload,
    val document: Document? = null,
    override val refToMessageId: String? = null
) : EbmsMessage(
    requestId,
    messageId,
    conversationId,
    cpaId,
    addressing,
    document,
    refToMessageId
) {

    override fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator.validate(signatureDetails, this.dokument!!, listOf(payload!!))
        log.info("Signatur OK")
    }

    fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createResponseHeader(this))
    }

    private fun createEbmsDocument(ebxmlDokument: Header): EbMSDocument {
        val envelope = Envelope()
        val attachmentUid = UUID.randomUUID().toString()
        envelope.header = ebxmlDokument

        envelope.body = Body().apply {
            this.any.add(
                Manifest().apply {
                    this.reference.add(
                        Reference().apply {
                            this.href = "cid:" + attachmentUid
                            this.type = "simple"
                        }
                    )
                }
            )
        }
        val dokument = getDocumentBuilder().parse(InputSource(StringReader(marshal(envelope))))
        return EbMSDocument(
            UUID.randomUUID().toString(),
            dokument,
            listOf(EbmsAttachment(this.payload.payload, payload.contentType, payload.contentId))
        )
    }
}
