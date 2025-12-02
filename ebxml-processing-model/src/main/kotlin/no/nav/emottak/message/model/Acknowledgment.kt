package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.utils.common.model.Addressing
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Description
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.From
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.PartyId
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.time.Instant
import java.util.Date

data class Acknowledgment(
    override val requestId: String,
    override val messageId: String,
    override val refToMessageId: String,
    override val conversationId: String,
    override val cpaId: String,
    override val addressing: Addressing,
    override val description: List<Description>? = emptyList(),
    override val document: Document? = null,
    override val sentAt: Instant? = null,
    val referenceList: NodeList? = null
) : EbmsMessage() {

    override fun toEbmsDokument(): EbmsDocument {
        return createEbmsDocument(
            createMessageHeader().apply {
                this.any.add(createAcknowledgementElement())
            }
        ).also {
            val acknowledgmentElement = it.document
                .getElementsByTagNameNS(EbXMLConstants.OASIS_EBXML_MSG_HEADER_XSD_NS_URI, "Acknowledgment")
                .item(0)
            for (i in 0 until (referenceList?.length ?: 0)) {
                acknowledgmentElement
                    .appendChild(
                        it.document.importNode(referenceList?.item(i), true)
                    )
            }
        }
    }

    fun createAcknowledgementElement(): Acknowledgment =
        Acknowledgment().apply {
            version = "2.0"
            isMustUnderstand = true
            timestamp = Date.from(Instant.now())
            refToMessageId = this@Acknowledgment.refToMessageId
            from = From().apply {
                this.partyId.addAll(
                    this@Acknowledgment.addressing.from.partyId.map {
                        PartyId().apply {
                            this.value = it.value
                            this.type = it.type
                        }
                    }
                )
            }
        }
}
