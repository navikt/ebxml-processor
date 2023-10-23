package no.nav.emottak.ebms.model

import no.nav.emottak.Event
import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.DekrypteringProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.From
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageData
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
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
        return EbMSMessageError(MessageHeader(), ErrorList())
    }

    fun createAcknowledgment(): EbmsAcknowledgment {
        val acknowledgment = this.createAcknowledgementJaxB()
        val messageHeader = this.createAcknowledgmentMessageHeader()
        return EbmsAcknowledgment(messageHeader,acknowledgment)
    }

    private fun createAcknowledgmentMessageHeader(): MessageHeader {
        val messageHeader = MessageHeader()
        messageHeader.conversationId = this.messageHeader.conversationId
        messageHeader.from = From().also {
            it.partyId.addAll(this.messageHeader.to.partyId)
            it.role = "ACK_SENDER"
        }
        messageHeader.to = To().also {
            it.partyId.addAll(this.messageHeader.from.partyId)
            it.role = "ACK_RECEIVER"
        }
        messageHeader.service = this.messageHeader.service
        messageHeader.action = this.messageHeader.action
        messageHeader.cpaId = this.messageHeader.cpaId
        messageHeader.messageData = MessageData().also {
            it.messageId = this.messageHeader.messageData.messageId + "_RESPONSE"
            it.refToMessageId = this.messageHeader.messageData.messageId
            it.timestamp = Date.from(Instant.now())
        }
        return messageHeader
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
                CPAValidationProcessor(this),
                SertifikatsjekkProcessor(this),
                DekrypteringProcessor(this)
            )
                .forEach { it.processWithEvents() }
            this.createAcknowledgment()
        } catch (ex: Exception) {
            createFail()
        }
    }

}
