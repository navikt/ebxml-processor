package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.createResponseHeader
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.ebms.xml.marshal
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Feil
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

open class EbmsMessage(
    open val requestId: String,
    open val messageId: String,
    open val conversationId: String,
    open val cpaId: String,
    open val addressing: Addressing,
    val dokument: Document? = null,
    open val refToMessageId: String? = null
) {

    open fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
        no.nav.emottak.ebms.model.log.info("Signatur OK")
    }

    open fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createResponseHeader(this), null)
    }

    open fun createFail(errorList: List<Feil>): EbmsFail {
        return EbmsFail(
            requestId,
            UUID.randomUUID().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.copy(to = addressing.from, from = addressing.to),
            errorList
        )
    }
}

fun createEbmsDocument(ebxmlDokument: Header, payload: Payload? = null): EbMSDocument {
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
    val payloads = if (payload != null) listOf(EbmsAttachment(payload.payload, payload.contentType, payload.contentId)) else listOf()
    return EbMSDocument(
        UUID.randomUUID().toString(),
        dokument,
        payloads
    )
}
