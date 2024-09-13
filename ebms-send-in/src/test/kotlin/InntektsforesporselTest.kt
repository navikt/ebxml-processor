import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.feil.v1.ObjectFactory
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListe
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeBrukerIkkeFunnet
import no.nav.ekstern.virkemiddelokonomi.tjenester.utbetaling.v1.FinnUtbetalingListeFeil
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.utbetaling.UtbetalingXmlMarshaller
import no.nav.emottak.utbetaling.msgHeadResponse
import no.nav.emottak.utbetaling.unmarshal
import org.junit.jupiter.api.Test

class InntektsforesporselTest {

    @Test
    fun unmarshalMsgHead() {
        val response = unmarshal(
            String(
                this::class.java.classLoader.getResourceAsStream("inntektsforesporsel/inntektsforesporsel.xml")!!
                    .readAllBytes()
            ),
            MsgHead::class.java
        )
        val request = response.document.map { doc -> doc.refDoc.content.any }.first().first()
        assert(request is FinnUtbetalingListe)
    }

    @Test
    fun canMarshalFaultInfo() {
        val stelvioFault = ObjectFactory().createBrukerIkkeFunnet()
        stelvioFault.errorMessage = "Ayylmaoo"
        val brukerIkkeFunnetException = FinnUtbetalingListeBrukerIkkeFunnet("Ayylmaoo", stelvioFault)

        val finnUtbetalingListeFeil = FinnUtbetalingListeFeil()
        finnUtbetalingListeFeil.finnUtbetalingListebrukerIkkeFunnet = brukerIkkeFunnetException.faultInfo

        val msgHeadRequest = UtbetalingXmlMarshaller.unmarshal(msgHeadEksempel, MsgHead::class.java)
        val msgHeadResponse = msgHeadResponse(
            msgHeadRequest,
            SendInRequest(
                "my-message-id",
                "my-conversation-id",
                "my-payload-id",
                msgHeadEksempel.toByteArray(),
                Addressing(
                    Party(listOf(PartyId("org", "12345")), "mottaker"),
                    Party(listOf(PartyId("org", "67890")), "innsender"),
                    "Inntektsforesporsel",
                    "Inntektsforesporsel"
                ),
                "dummycpa",
                EbmsProcessing()
            ),
            finnUtbetalingListeFeil
        )

        val feilElementString = String(
            UtbetalingXmlMarshaller.marshalToByteArray(msgHeadResponse)
        )
        println(feilElementString)
    }

    val msgHeadEksempel = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<MsgHead xmlns:kith=\"http://www.kith.no/xmlstds\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema.xsd\"\n" +
        "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.kith.no/xmlstds/msghead/2006-05-24\"\n" +
        "         xsi:schemaLocation=\"http://www.kith.no/xmlstds/msghead/2006-05-24 MsgHead-v1_2.xsd\">\n" +
        "    <MsgInfo>\n" +
        "        <Type V=\"InntektsForesporsel\" DN=\"Forespørsel på borgers ytelser\"/>\n" +
        "        <MIGversion>v1.2 2006-05-24</MIGversion>\n" +
        "        <GenDate>2024-06-17T08:31:10</GenDate>\n" +
        "        <MsgId>309c5972-b5be-4b60-b172-094c5d236713</MsgId>\n" +
        "        <ProcessingStatus V=\"P\" DN=\"Produksjon\"/>\n" +
        "        <RequestedPriority V=\"N\" DN=\"Normal\"/>\n" +
        "        <Ack V=\"J\" DN=\"Ja\"/>\n" +
        "        <Sender>\n" +
        "            <Organisation>\n" +
        "                <OrganisationName>Tromsø kommune</OrganisationName>\n" +
        "                <TypeOrganisation V=\"102\" S=\"1.2.3.4.5.6.7.8\" DN=\"Kommune\"/>\n" +
        "                <Ident>\n" +
        "                    <Id>940101808</Id>\n" +
        "                    <TypeId V=\"ENH\" S=\"1.2.3.4.5.6.7.8\" DN=\"Organisasjonsnummeret i Enhetsregister\"/>\n" +
        "                </Ident>\n" +
        "                <Ident>\n" +
        "                    <Id>1234</Id>\n" +
        "                    <TypeId V=\"HER\" S=\"1.2.3.4.5.6.7.8\" DN=\"HER-id\"/>\n" +
        "                </Ident>\n" +
        "                <Address>\n" +
        "                    <Type V=\"PST\" DN=\"Postadresse\"/>\n" +
        "                    <County V=\"5501\" DN=\"Tromsø\"/>\n" +
        "                </Address>\n" +
        "                <Organisation>\n" +
        "                    <OrganisationName>Økonomi og oppgjør</OrganisationName>\n" +
        "                    <Ident>\n" +
        "                        <Id>12345</Id>\n" +
        "                        <TypeId V=\"HER\" S=\"1.2.3.4.5.6.7.8\" DN=\"HER-id\"/>\n" +
        "                    </Ident>\n" +
        "                    <Address>\n" +
        "                        <Type V=\"PST\" DN=\"Postadresse\"/>\n" +
        "                        <County V=\"5501\" DN=\"Tromsø\"/>\n" +
        "                    </Address>\n" +
        "                </Organisation>\n" +
        "            </Organisation>\n" +
        "        </Sender>\n" +
        "        <Receiver>\n" +
        "            <Organisation>\n" +
        "                <OrganisationName>NAV Pensjon</OrganisationName>\n" +
        "                <Ident>\n" +
        "                    <Id>889640782</Id>\n" +
        "                    <TypeId V=\"ENH\" S=\"1.2.3.4.5.6.7.8\" DN=\"Organisasjonsnummeret i Enhetsregister\"/>\n" +
        "                </Ident>\n" +
        "                <Ident>\n" +
        "                    <Id>12345</Id>\n" +
        "                    <TypeId V=\"HER\" S=\"1.2.3.4.5.6.7.8\" DN=\"HER-id\"/>\n" +
        "                </Ident>\n" +
        "            </Organisation>\n" +
        "        </Receiver>\n" +
        "        <Patient>\n" +
        "            <FamilyName>Nordmann</FamilyName>\n" +
        "            <GivenName>Ola</GivenName>\n" +
        "            <Ident>\n" +
        "                <Id>12345678965</Id>\n" +
        "                <TypeId V=\"FNR\" S=\"1.2.3.4.5.6.7.8\" DN=\"Fødselsnummer\"/>\n" +
        "            </Ident>\n" +
        "            <Address>\n" +
        "                <Type V=\"H\" DN=\"Bostedsadresse\"/>\n" +
        "                <StreetAdr>Veigaten 1</StreetAdr>\n" +
        "                <PostalCode>1234</PostalCode>\n" +
        "                <City>OSLO</City>\n" +
        "            </Address>\n" +
        "            <TeleCom>\n" +
        "                <TypeTelecom V=\"H\" DN=\"Hjemme eller ukjent\"/>\n" +
        "                <TeleAddress V=\"tel:91381895\"/>\n" +
        "            </TeleCom>\n" +
        "        </Patient>\n" +
        "    </MsgInfo>\n" +
        "    <Document>\n" +
        "        <Consent V=\"1\" S=\"1.2.3.4.5.6.7.8\" DN=\"Ja\"/>\n" +
        "        <RefDoc>\n" +
        "            <IssueDate V=\"2024-06-17T08:31:10\"/>\n" +
        "            <MsgType V=\"XML\" DN=\"XML-instans\"/>\n" +
        "            <Content>\n" +
        "                <finnUtbetalingListe xmlns:xsd=\"http://www.w3.org/2001/XMLSchema.xsd\"\n" +
        "                                     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "                                     xmlns:kith=\"http://www.kith.no/xmlstds\"\n" +
        "                                     xmlns=\"http://nav.no/ekstern/virkemiddelokonomi/tjenester/utbetaling/v1\"\n" +
        "                                     xsi:schemaLocation=\"http://nav.no/ekstern/virkemiddelokonomi/tjenester/utbetaling/v1 inntektsforesporsel.xsd\">\n" +
        "                    <request xmlns=\"\">\n" +
        "                        <bruker>\n" +
        "                            <brukerId>12345678965</brukerId>\n" +
        "                            <brukertypeKode>PERSON</brukertypeKode>\n" +
        "                        </bruker>\n" +
        "                        <hjemmelshaverKode>KOMMUNE</hjemmelshaverKode>\n" +
        "                        <posteringsperiodefilter>\n" +
        "                            <fom>2024-06-01</fom>\n" +
        "                            <tom>2024-06-12</tom>\n" +
        "                        </posteringsperiodefilter>\n" +
        "                    </request>\n" +
        "                </finnUtbetalingListe>\n" +
        "            </Content>\n" +
        "        </RefDoc>\n" +
        "    </Document>\n" +
        "</MsgHead>"
}
