package no.nav.emottak.cpa

import no.nav.emottak.cpa.feil.CpaValidationException
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.decodeBase64
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class CPAUtilTest {
    @Test
    fun `Hent public sertifikat for signatursjekk`() {
        val expectedCertificate = createX509Certificate(
            decodeBase64("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".toByteArray())
        )
        val cpa = TestUtil.createValidTestCPA()
        val cert = createX509Certificate(cpa.getPartyInfoByTypeAndID(partyType = "HER", partyId = "8141253").getCertificateForSignatureValidation(role = "Behandler", service = "BehandlerKrav", action = "OppgjorsMelding").certificate)
        assertEquals(expectedCertificate, cert)
    }

    @Test
    fun `Hent public sertifikat for kryptering`() {
        val expectedCertificate = createX509Certificate(
            decodeBase64("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".toByteArray())
        )
        val cpa = TestUtil.createValidTestCPA()
        val cert = createX509Certificate(cpa.getPartyInfoByTypeAndID(partyType = "HER", partyId = "8141253").getCertificateForEncryption())
        assertEquals(expectedCertificate, cert)
    }

    @Test
    fun `Hent public sertifikat for signering`() {
        val expectedCertificate = createX509Certificate(
            decodeBase64("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".toByteArray())
        )
        val cpa = TestUtil.createValidTestCPA()
        val cert = createX509Certificate(cpa.getPartyInfoByTypeAndID(partyType = "HER", partyId = "79768").getCertificateForSignatureValidation(role = "KontrollUtbetaler", service = "BehandlerKrav", action = "Svarmelding").certificate)
        assertEquals(expectedCertificate, cert)
    }

    @Test
    fun `Hent partyInfo med korrekt type og korrekt ID`() {
        val cpa = TestUtil.createValidTestCPA()
        val partyInfo = cpa.getPartyInfoByTypeAndID("HER", "79768")
        assertEquals("HER", partyInfo.partyId.first().type)
        assertEquals("79768", partyInfo.partyId.first().value)
    }

    @Test
    fun `Hent partyInfo med feil type og korrekt ID`() {
        val cpa = TestUtil.createValidTestCPA()
        val type = "orgnummer"
        val id = "79768"
        val exception = assertThrows<CpaValidationException> {
            cpa.getPartyInfoByTypeAndID(type, id)
        }
        assertEquals("PartyID med type $type og id $id eksisterer ikke i CPA", exception.message)
    }
}
