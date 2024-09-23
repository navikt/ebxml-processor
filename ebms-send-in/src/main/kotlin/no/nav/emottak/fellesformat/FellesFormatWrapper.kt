package no.nav.emottak.fellesformat

import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.util.toXMLGregorianCalendar
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import no.trygdeetaten.xml.eiff._1.ObjectFactory
import java.time.Instant

private val fellesFormatFactory = ObjectFactory()

fun EIFellesformat.addressing(toParty: Party): Addressing {
    val sender = this.msgHead.msgInfo.sender
    val reciever = this.msgHead.msgInfo.receiver
    val fromList = sender.organisation.ident.map { PartyId(it.typeId.v, it.id) }.toList()
    val partyFrom = Party(fromList, this.mottakenhetBlokk.ebRole)
    val toList = reciever.organisation.ident.map { PartyId(it.typeId.v, it.id) }.toList()
    val partyTo = Party(toList, toParty.role)
    return Addressing(partyTo, partyFrom, this.mottakenhetBlokk.ebService, this.mottakenhetBlokk.ebAction)
}

fun wrapMessageInEIFellesFormat(sendInRequest: SendInRequest): EIFellesformat =
    fellesFormatFactory.createEIFellesformat().also {
        it.mottakenhetBlokk = createFellesFormatMottakEnhetBlokk(sendInRequest)
        it.msgHead = unmarshal(sendInRequest.payload.toString(Charsets.UTF_8), MsgHead::class.java)
    }

private fun createFellesFormatMottakEnhetBlokk(sendInRequest: SendInRequest): EIFellesformat.MottakenhetBlokk =
    fellesFormatFactory.createEIFellesformatMottakenhetBlokk().also {
        it.ebXMLSamtaleId = sendInRequest.conversationId
        it.ebAction = sendInRequest.addressing.action
        it.ebService = sendInRequest.addressing.service
        it.ebRole = sendInRequest.addressing.from.role
        it.avsender = "TODO1" // Hentes fra from. Usikker på hvilket felt siden det kan være flere.
        it.avsenderRef = "TODO2" // Hentet fra cert: Eksempelverdi: "SERIALNUMBER=132547698, CN=Blå &amp; Bjørnebær AS, O=Blå &amp; Bjørnebær AS, C=NO"
        it.mottaksId = sendInRequest.messageId
        it.mottattDatotid = Instant.now().toXMLGregorianCalendar()
        it.ediLoggId = sendInRequest.messageId
        it.avsenderFnrFraDigSignatur = "TODO3"
        it.avsenderOrgNrFraDigSignatur = "TODO4"
        it.herIdentifikator = "TODO5" // Avsender HER ID?
        it.orgNummer = "TODO6" // Avsender?
        it.meldingsType = "xml"
        it.partnerReferanse = sendInRequest.cpaId
    }
