package no.nav.emottak.ebms.model

import no.nav.emottak.constants.EbXMLConstants
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.SignatureDetails
import org.w3c.dom.Document
import java.util.*

data class PayloadMessage(
    override val requestId: String,
    override val messageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val payload: EbmsAttachment,
    override val dokument: Document? = null,
    override val refToMessageId: String? = null

) : EbmsMessage() {

    override fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator.validate(signatureDetails, this.dokument!!, listOf(payload!!))
        log.info("Signatur OK")
    }

    override fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader(), this.payload)
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
