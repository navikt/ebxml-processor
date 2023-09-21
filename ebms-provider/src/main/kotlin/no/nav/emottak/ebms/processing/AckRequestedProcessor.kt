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
import java.util.*
import kotlin.coroutines.Continuation

class AckRequestedProcessor(): Processor {

    fun createAcknowledgement(envelope: Envelope): Acknowledgment {
        val acknowledgment = Acknowledgment()
        acknowledgment.id = "ACK_ID" // Identifier for Acknowledgment elementet, IKKE message ID. // TODO avklar, dette er såvidt jeg vet en arbitrær verdi?
        acknowledgment.version = envelope.getVersion()
        acknowledgment.isMustUnderstand = true // Alltid
        acknowledgment.actor = envelope.getActor()
        acknowledgment.timestamp = Date.from(Instant.now()) // TODO dette skal være message received date, hente fra context?
        acknowledgment.refToMessageId = envelope.getMessageId()
        acknowledgment.from = envelope.getFrom()
        if(envelope.getAckRequestedSigned()) {
            // TODO vi må signere responsen, kan kanskje alltid gjøres uansett?
            acknowledgment.reference.addAll(getReferences())
        }
        //acknowledgment.otherAttributes
        return acknowledgment
    }

    fun getReferences():  List<@NotNull ReferenceType> {
        return emptyList() // TODO XMLDSIG elements fra signaturen vår
    }

    override fun process() {
        TODO("Not yet implemented")
    }
}

