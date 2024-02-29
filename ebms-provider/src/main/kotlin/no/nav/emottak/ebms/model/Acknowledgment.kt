package no.nav.emottak.ebms.model

import no.nav.emottak.melding.model.Addressing
import org.w3c.dom.Document

class Acknowledgment(
    requestId: String,
    messageId: String,
    refToMessageId: String,
    conversationId: String,
    cpaId: String,
    addressing: Addressing,
    document: Document? = null
) : EbmsMessage(
    requestId,
    messageId,
    conversationId,
    cpaId,
    addressing,
    document
) {

    override fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader(withAcknowledgmentElement = true))
    }
}
