package no.nav.emottak.ebms.model

import no.nav.emottak.Event
import no.nav.emottak.constants.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.constants.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.constants.EbXMLConstants.MESSAGE_ERROR_ACTION
import no.nav.emottak.ebms.ebxml.createResponseHeader
import no.nav.emottak.ebms.ebxml.getAckRequestedSigned
import no.nav.emottak.ebms.processing.DekrypteringProcessor
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList

// EbmsPayloadMessage
class EbmsPayloadMessage(
    val contentID: String,
    override val dokument: Document,
    override val messageHeader: MessageHeader,
    val ackRequested: AckRequested? = null,
    val attachments: List<EbmsAttachment>,
    val mottatt: LocalDateTime
) : EbmsBaseMessage {
    private val eventLogg: ArrayList<Event> = ArrayList() // TODO tenk over
    fun addHendelse(event: Event) {
        eventLogg.add(event)
    }

    fun createFail(): EbmsMessageError {
        return EbmsMessageError(this.createErrorMessageHeader(), ErrorList())
    }

    fun createAcknowledgment(): EbmsAcknowledgment {
        return EbmsAcknowledgment(this.createAcknowledgmentMessageHeader(), this.createAcknowledgementJaxB())
    }

    private fun createErrorMessageHeader(): MessageHeader {
        return this.messageHeader.createResponseHeader(
            newAction = MESSAGE_ERROR_ACTION,
            newService = EBMS_SERVICE_URI
        )
    }
    private fun createAcknowledgmentMessageHeader(): MessageHeader {
        return this.messageHeader.createResponseHeader(
            newAction = ACKNOWLEDGMENT_ACTION,
            newService = EBMS_SERVICE_URI
        )
    }

    private fun createAcknowledgementJaxB(): Acknowledgment {
        val acknowledgment = Acknowledgment()
        acknowledgment.id = "ACK_ID" // Identifier for Acknowledgment elementet, IKKE message ID. // TODO avklar, dette er såvidt jeg vet en arbitrær verdi?
        acknowledgment.version = this.messageHeader.version
        acknowledgment.isMustUnderstand = true // Alltid
        acknowledgment.actor = this.ackRequested!!.actor
        acknowledgment.timestamp = Date.from(this.mottatt.toInstant(ZoneOffset.UTC))
        acknowledgment.refToMessageId = this.messageHeader.messageData.messageId
        acknowledgment.from = this.messageHeader.from
        if (this.messageHeader.getAckRequestedSigned() == true) {
            // TODO vi må signere responsen, kan kanskje alltid gjøres uansett?
            acknowledgment.reference.addAll(emptyList())
        }
        // acknowledgment.otherAttributes
        return acknowledgment
    }

    fun process(): EbmsBaseMessage {
        return try {
            listOf(
                DekrypteringProcessor(this)
            )
                .forEach { it.processWithEvents() }
            this.createAcknowledgment()
        } catch (ex: Exception) {
            createFail()
        }
    }
}