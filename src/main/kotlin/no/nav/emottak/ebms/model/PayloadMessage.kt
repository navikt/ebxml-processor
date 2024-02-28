package no.nav.emottak.ebms.model

import no.nav.emottak.constants.EbXMLConstants
import no.nav.emottak.ebms.createResponseHeader
import no.nav.emottak.ebms.ebxml.createResponseHeader
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.SignatureDetails
import org.w3c.dom.Document
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

    override fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createResponseHeader(this), this.payload)
    }

    fun createAcknowledgment(): Acknowledgment {
        return Acknowledgment(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.copy(
                service = EbXMLConstants.EBMS_SERVICE_URI,
                action = EbXMLConstants.ACKNOWLEDGMENT_ACTION
            )
        )
    }
}
