package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import org.w3c.dom.Document
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class PayloadMessage(
    override val requestId: String,
    override val messageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    val payload: EbmsAttachment,
    override val dokument: Document? = null,
    override val refToMessageId: String? = null,
    override val sentAt: Instant? = null,
    val duplicateElimination: Boolean

) : EbmsMessage() {

    override fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader(), this.payload)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createAcknowledgment(): Acknowledgment {
        return Acknowledgment(
            Uuid.random().toString(),
            Uuid.random().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.replyTo(
                service = EbXMLConstants.EBMS_SERVICE_URI,
                action = EbXMLConstants.ACKNOWLEDGMENT_ACTION
            )
        )
    }
}
