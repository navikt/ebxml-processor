package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.utils.common.model.Addressing
import org.w3c.dom.Document
import org.w3c.dom.NodeList
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
    override val document: Document? = null,
    override val refToMessageId: String? = null,
    override val sentAt: Instant? = null,
    val timeToLiveSeconds: Long? = null,
    val duplicateElimination: Boolean,
    val ackRequested: Boolean = false
) : EbmsMessage() {

    override fun toEbmsDokument(): EbmsDocument {
        return createEbmsDocument(
            createMessageHeader(
                withAckRequestedElement = ackRequested,
                withDuplicateEliminationElement = duplicateElimination
            ),
            this.payload
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createAcknowledgment(): Acknowledgment {
        return Acknowledgment(
            requestId = Uuid.random().toString(),
            messageId = Uuid.random().toString(),
            refToMessageId = this.messageId,
            conversationId = this.conversationId,
            cpaId = this.cpaId,
            addressing = this.addressing.replyTo(
                service = EbXMLConstants.EBMS_SERVICE_URI,
                action = EbXMLConstants.ACKNOWLEDGMENT_ACTION
            ),
            referenceList = this.document?.getSignatureReferenceNodeList()
        )
    }

    private fun Document.getSignatureReferenceNodeList(): NodeList =
        this.getElementsByTagNameNS(EbXMLConstants.XMLDSIG_NS_URI, EbXMLConstants.XMLDSIG_TAG_REFERENCE)
}
