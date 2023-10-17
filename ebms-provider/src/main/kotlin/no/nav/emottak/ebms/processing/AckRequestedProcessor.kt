package no.nav.emottak.ebms.processing

import com.github.labai.jsr305x.api.NotNull
import no.nav.emottak.Event
import no.nav.emottak.ebms.model.EbMSMessage
import no.nav.emottak.ebms.model.getAckRequestedSigned
import no.nav.emottak.ebms.xml.marshal
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.w3._2000._09.xmldsig_.ReferenceType
import java.time.ZoneOffset
import java.util.*

class AckRequestedProcessor(ebMSMessage: EbMSMessage): EbMSMessageProcessor(ebMSMessage) {
    override fun process() {
        sjekkBetingelser()
        if (ebMSMessage.ackRequested != null) {
            log.info("Lager acknowledgement...")
            val createdAcknowledgement = createAcknowledgement(ebMSMessage)
            log.info(createdAcknowledgement.toXmlString())
        }
        // TODO resten
    }

    private fun sjekkBetingelser() {
        // TODO: Vurder å fjerne dette. Unødvendig defensivt å kjøre slik kode dersom flow i utgangspunktet skal være korrekt.
        // Merk. Ifølge EH spec (s. 15) skal ack først sendes når dekryptering av payload(?) har gått bra. https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/EBXMLrammeverk/HIS%201037_2011%20Rammeverk%20for%20meldingsutveksling%20v1.1%20-oppdatert.pdf#page=15
        val requiredEvent =
            Event(
                Event.defaultProcessName(PayloadProcessor::class.java),
                Event.Status.OK,
                correlationId = korrelasjonsId()
            )
        if (!ebMSMessage.harHendelse(requiredEvent)) {
            throw RuntimeException("AckRequested mangler påkrevd hendelse $requiredEvent")
        }
    }

    fun Acknowledgment.toXmlString(): String {
        return marshal(this)
    }

    fun createAcknowledgement(message: EbMSMessage): Acknowledgment {
        // se: https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/EBXMLrammeverk/HIS%201037_2011%20Rammeverk%20for%20meldingsutveksling%20v1.1%20-oppdatert.pdf#page=42
        val acknowledgment = Acknowledgment()
        acknowledgment.id = "ACK_ID" // Identifier for Acknowledgment elementet, IKKE message ID. // TODO avklar, dette er såvidt jeg vet en arbitrær verdi?
        acknowledgment.version = message.messageHeader.version // Alltid "2.0" egentlig
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

