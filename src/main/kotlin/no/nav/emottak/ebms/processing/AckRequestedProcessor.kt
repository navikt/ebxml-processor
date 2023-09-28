package no.nav.emottak.ebms.processing

import com.github.labai.jsr305x.api.NotNull
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.model.getAckRequestedSigned
import no.nav.emottak.ebms.xml.marshal
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.w3._2000._09.xmldsig_.ReferenceType
import java.time.ZoneOffset
import java.util.*

class AckRequestedProcessor(ebMSMessage: EbMSMessage): Processor(ebMSMessage) {

    override fun process() {
        // Merk. Ifølge EH spec (s. 15) skal ack først sendes når dekryptering av payload(?) har gått bra.
        if (ebMSMessage.ackRequested != null) {
            log.info("Lager acknowledgement...")
            val createdAcknowledgement = createAcknowledgement(ebMSMessage)
            log.info(createdAcknowledgement.toXmlString())
        }
        // TODO resten
    }
    fun Acknowledgment.toXmlString(): String {
        return marshal(this)
    }

    fun createAcknowledgement(message: EbMSMessage): Acknowledgment {
        val acknowledgment = Acknowledgment()
        acknowledgment.id = "ACK_ID" // Identifier for Acknowledgment elementet, IKKE message ID. // TODO avklar, dette er såvidt jeg vet en arbitrær verdi?
        acknowledgment.version = message.messageHeader.version
        acknowledgment.isMustUnderstand = true // Alltid
        acknowledgment.actor = message.ackRequested!!.actor
        acknowledgment.timestamp = Date.from(message.mottatt.toInstant(ZoneOffset.UTC))
        acknowledgment.refToMessageId = message.messageHeader.messageData.messageId
        acknowledgment.from = message.messageHeader.from
        if(message.messageHeader.getAckRequestedSigned() == true) {
            // TODO vi må signere responsen, kan kanskje alltid gjøres uansett?
            acknowledgment.reference.addAll(getReferences())
        }
        //acknowledgment.otherAttributes
        return acknowledgment
    }

    fun getReferences():  List<@NotNull ReferenceType> {
        return emptyList() // TODO XMLDSIG elements fra signaturen vår
    }
}

