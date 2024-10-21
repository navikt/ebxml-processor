package no.nav.emottak.message.model


import no.nav.emottak.message.ebxml.EbXMLConstants
import org.w3c.dom.Document
import java.util.UUID

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
