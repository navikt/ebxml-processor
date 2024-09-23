package no.nav.emottak.fellesformat


import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.EbmsProcessing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.pasientliste.PasientlisteService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PasientlisteServiceTest {

    @Test
    fun testpasientListe() {

        val fagmelding = ClassLoader.getSystemResourceAsStream("hentpasientliste/hentpasientliste-payload.xml")
        val sendIndRequest = mockSendInRequest("HentPasientliste", fagmelding.readAllBytes())

        try {
            PasientlisteService.pasientlisteForesporsel(sendIndRequest)
        } catch (exception: RuntimeException) {
            Assertions.assertEquals(exception.message, PasientlisteService.CONFLICT_SIGNING_SSN)
        }


    }


    fun mockSendInRequest(action: String, fagmelding: ByteArray): SendInRequest = SendInRequest(
        "dummy", "dummy", "dummy", fagmelding, Addressing(
            Party(
                listOf(PartyId("dummy", "dummy")), "dummyRole"
            ), Party(
                listOf(PartyId("dummy", "dummy")), "dummyRole"
            ), "dummyService", action
        ), "dummycpaid",
        EbmsProcessing(), "123456789"
    )

}