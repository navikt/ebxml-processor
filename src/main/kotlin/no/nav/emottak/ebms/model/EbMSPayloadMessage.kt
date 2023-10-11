package no.nav.emottak.ebms.model

import no.nav.emottak.Event
import no.nav.emottak.ebms.processing.CPAValidationProcessor
import no.nav.emottak.ebms.processing.SertifikatsjekkProcessor
import no.nav.emottak.ebms.processing.SignatursjekkProcessor
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayList


//EbmsPayloadMessage
class EbMSPayloadMessage(private val dokument:Document, override val messageHeader: MessageHeader,
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
        return MessageHeader()
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
        try {
           listOf(
                CPAValidationProcessor(this),
                SertifikatsjekkProcessor(this),
                SignatursjekkProcessor(dokument, this)
           )
               .forEach { it.processWithEvents() }
           return this.createAcknowledgment()
       }catch (ex: Exception) {
           return createFail()
       }
    }

}
