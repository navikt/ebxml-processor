package no.nav.emottak.ebms.model

import no.nav.emottak.EBMS_SERVICE_URI
import no.nav.emottak.Event
import no.nav.emottak.ebms.createResponseHeader
import no.nav.emottak.ebms.processing.DekrypteringProcessor
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.From
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageData
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Service
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.To
import org.w3c.dom.Document
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList


//EbmsPayloadMessage
class EbMSPayloadMessage(
    override val dokument:Document,
    override val messageHeader: MessageHeader,
    val ackRequested: AckRequested? = null,
    val attachments: List<EbMSAttachment>,
    val mottatt:  LocalDateTime
                  ) : EbMSBaseMessage
{
    private val eventLogg: ArrayList<Event> = ArrayList() // TODO tenk over
    fun addHendelse(event: Event) {
        eventLogg.add(event)
    }


    fun createFail(): EbMSMessageError {
        return EbMSMessageError(this.createErrorMessageHeader(), ErrorList())
    }

    fun createAcknowledgment(): EbmsAcknowledgment {
        return EbmsAcknowledgment(this.createAcknowledgmentMessageHeader(),this.createAcknowledgementJaxB())
    }

    private fun createErrorMessageHeader(): MessageHeader {
        return this.messageHeader.createResponseHeader(
            newFromRole = "ERROR_RESPONDER", newToRole = "ERROR_RECEIVER", newAction = "”MessageError”", newService = EBMS_SERVICE_URI)
    }
    private fun createAcknowledgmentMessageHeader(): MessageHeader {
        return this.messageHeader.createResponseHeader(
            newFromRole = "ACK_RESPONDER", newToRole = "ACK_RECEIVER", newAction = "Acknowledgment", newService = EBMS_SERVICE_URI)
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
        if(this.messageHeader.getAckRequestedSigned() == true) {
            // TODO vi må signere responsen, kan kanskje alltid gjøres uansett?
            acknowledgment.reference.addAll(emptyList())
        }
        //acknowledgment.otherAttributes
        return acknowledgment
    }

    fun process() : EbMSBaseMessage {
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