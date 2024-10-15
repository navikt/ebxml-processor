package no.nav.emottak.ebms.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import no.nav.emottak.constants.EbXMLConstants
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.EbmsProcessing
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.SignatureDetails
import org.w3c.dom.Document
import java.util.UUID

@Serializable
@SerialName("PayloadMessage")
data class PayloadMessage(
    override val requestId: String,
    override val messageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val payload: EbmsAttachment,
    @Transient
    override val dokument: Document? = null,
    override val refToMessageId: String? = null,
    val containsApprec: Boolean = false

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

    fun createNegativApprec(apprecPayload: Payload, errorAction: String): PayloadMessage =
        this.copy(
            payload = apprecPayload,
            addressing = this.addressing.copy(
                action = errorAction,
                to = this.addressing.from,
                from = this.addressing.to
            ),
            messageId = UUID.randomUUID().toString(),
            refToMessageId = this.messageId,
            containsApprec = true
        )
}

@Serializable
data class EbxmlProcessingResponse(
    val processingResponse: EbmsMessage,
    val ebmsProcessing: EbmsProcessing = EbmsProcessing(),
    val ebxmlFail: EbmsFail,
    val soapFault: SoapFault
)
