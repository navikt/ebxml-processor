package no.nav.emottak.fellesformat

import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.frikort.unmarshal
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.SendInRequest
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import no.trygdeetaten.xml.eiff._1.ObjectFactory
import java.time.Instant
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

private val fellesFormatFactory = ObjectFactory()

fun wrapMessageInEIFellesFormat(sendInRequest: SendInRequest): EIFellesformat =
    fellesFormatFactory.createEIFellesformat().also {
        it.mottakenhetBlokk = createFellesFormatMottakEnhetBlokk(sendInRequest.messageId, sendInRequest.conversationId, sendInRequest.addressing)
        it.msgHead = unmarshal(sendInRequest.payload.toString(Charsets.UTF_8), MsgHead::class.java)
    }

private fun createFellesFormatMottakEnhetBlokk(mottaksId: String, conversationId: String, addressing: Addressing): EIFellesformat.MottakenhetBlokk =
    fellesFormatFactory.createEIFellesformatMottakenhetBlokk().also {
        it.ebXMLSamtaleId = conversationId
        it.ebAction = addressing.action
        it.ebService = addressing.service
        it.ebRole = addressing.from.role
        it.avsender = "TODO"
        it.avsenderRef = "TODO"
        it.mottaksId = mottaksId
        it.mottattDatotid = Instant.now().toXMLGregorianCalendar()
        it.ediLoggId = "TODO"
        it.avsenderFnrFraDigSignatur = "TODO"
        it.avsenderOrgNrFraDigSignatur = "TODO"
        it.herIdentifikator = "TODO"
        it.orgNummer = "TODO"
        it.meldingsType = "TODO"
        it.partnerReferanse = "TODO"
    }

private fun Instant.toXMLGregorianCalendar(): XMLGregorianCalendar =
    DatatypeFactory.newInstance().newXMLGregorianCalendar(
        GregorianCalendar().also { it.setTimeInMillis(this.toEpochMilli()) }
    )
