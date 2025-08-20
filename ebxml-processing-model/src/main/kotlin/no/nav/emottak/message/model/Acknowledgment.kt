package no.nav.emottak.message.model

import no.nav.emottak.utils.common.model.Addressing
import org.w3c.dom.Document
import java.time.Instant

data class Acknowledgment(
    override val requestId: String,
    override val messageId: String,
    override val refToMessageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    override val dokument: Document? = null,
    override val sentAt: Instant? = null
) : EbmsMessage() {

    override fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader(withAcknowledgmentElement = true))
    }
}
