package no.nav.emottak.ebms.processing

import com.github.labai.jsr305x.api.NotNull
import no.nav.emottak.ebms.model.*
import no.nav.emottak.ebms.xml.xmlMarshaller
import org.apache.commons.lang3.StringUtils.isNotBlank
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ObjectFactory
import org.w3._2000._09.xmldsig_.ReferenceType
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.coroutines.Continuation

class AckRequestedProcessor(ebMSMessage: EbMSMessage): Processor(ebMSMessage) {

    override fun process() {
        //TODO("Not yet implemented")

    }
    // Merk. Ifølge EH spec (s. 15) skal ack først sendes når dekryptering av payload(?) har gått bra.
    fun createAcknowledgement(message: EbMSMessage): Acknowledgment {
        val acknowledgment = Acknowledgment()
        acknowledgment.id = "ACK_ID" // Identifier for Acknowledgment elementet, IKKE message ID. // TODO avklar, dette er såvidt jeg vet en arbitrær verdi?
        acknowledgment.version = message.messageHeader.version
        acknowledgment.isMustUnderstand = true // Alltid
        acknowledgment.actor = message.messageHeader.getActor()
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

