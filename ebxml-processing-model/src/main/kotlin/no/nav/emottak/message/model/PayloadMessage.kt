package no.nav.emottak.message.model


import kotlinx.serialization.Serializable
import no.nav.emottak.message.ebxml.EbXMLConstants
import org.w3c.dom.Document
import java.util.UUID

@Serializable
data class PayloadMessage(
    override val requestId: String,
    override val messageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val payload: EbmsAttachment,
    @Transient
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
