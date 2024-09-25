package no.nav.emottak

import io.mockk.every
import io.mockk.mockkObject
import no.nav.emottak.fellesformat.unmarshal
import no.nav.emottak.pasientliste.PasientlisteClient
import no.nav.emottak.pasientliste.PasientlisteService
import no.trygdeetaten.xml.eiff._1.EIFellesformat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PasientlisteServiceTest {

    @Test
    fun `Skall kaste exception hvis fnr fra sertifikat matcher ikke fnr fra fagmelding`() {
        val sendIndRequest = validSendInRequest.value
        try {
            PasientlisteService.pasientlisteForesporsel(sendIndRequest)
        } catch (exception: RuntimeException) {
            Assertions.assertEquals(exception.message, PasientlisteService.CONFLICT_SIGNING_SSN)
        }
        val fnrFraFagmeldingen = "17087000133"
        mockkObject(PasientlisteClient)
        every {
            PasientlisteClient.hentPasientliste(any())
        } answers {
            ClassLoader.getSystemResourceAsStream("hentpasientliste/pasientListeToktResponse.xml").readAllBytes().let {
                unmarshal(String(it), EIFellesformat::class.java)
            }
        }
        PasientlisteService.pasientlisteForesporsel(sendIndRequest.copy(signedOf = fnrFraFagmeldingen))
    }
}
