package no.nav.emottak.fellesformat

import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.ebms.log
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.util.getEnvVar
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
        it.mottakenhetBlokk = createFellesFormatMottakEnhetBlokk(sendInRequest.messageId, sendInRequest.conversationId, sendInRequest.addressing)
        it.msgHead = unmarshal(sendInRequest.payload.toString(Charsets.UTF_8), MsgHead::class.java)
    }.also {
        if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
            log.info("Sending in request with body: " + FellesFormatXmlMarshaller.marshal(it))
        }
    }

private fun createFellesFormatMottakEnhetBlokk(mottaksId: String, conversationId: String, addressing: Addressing): EIFellesformat.MottakenhetBlokk =
    fellesFormatFactory.createEIFellesformatMottakenhetBlokk().also {
        it.ebXMLSamtaleId = conversationId
        it.ebAction = addressing.action
        it.ebService = addressing.service
        it.ebRole = addressing.from.role
        it.avsender = "1"
        it.avsenderRef = "2"
        it.mottaksId = mottaksId
        it.mottattDatotid = Instant.now().toXMLGregorianCalendar()
        it.ediLoggId = "3"
        it.avsenderFnrFraDigSignatur = "4"
        it.avsenderOrgNrFraDigSignatur = "5"
        it.herIdentifikator = "6"
        it.orgNummer = "7"
        it.meldingsType = "8"
        it.partnerReferanse = "9"
    }
