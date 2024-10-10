package no.nav.emottak.ebms.model

import no.nav.emottak.message.model.Addressing
import org.w3c.dom.Document

data class Acknowledgment(
    override val requestId: String,
    override val messageId: String,
    override val refToMessageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    override val dokument: Document? = null
) : EbmsMessage() {

    override fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader(withAcknowledgmentElement = true))
    }
}
