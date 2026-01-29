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
    fun `Hent epostadresser fra CPA`() {
        val cpa = TestUtil.createValidTestCPA()

        val fromParty = cpa.getPartyInfoByTypeAndID("HER", "79768")
        val senderEmails = fromParty.getSendEmailAddress(
            "KontrollUtbetaler",
            "BehandlerKrav",
            "Svarmelding"
        )

        val toParty = cpa.getPartyInfoByTypeAndID("HER", "8141253")
        val signalEmails = toParty.getSignalEmailAddress("MessageError")
        val receiverEmails = toParty.getReceiveEmailAddress(
            "Behandler",
            "BehandlerKrav",
            "Svarmelding"
        )

        assertEquals(1, signalEmails.size, "signal recipient emails not found")
        assertEquals("mailto://example@example.com", signalEmails.first().emailAddress)
        assertEquals(1, senderEmails.size, "message sender emails not found")
        assertEquals("mailto://example2@example.com", senderEmails.first().emailAddress)
        assertEquals(1, receiverEmails.size, "message receiver emails not found")
        assertEquals("mailto://example@example.com", receiverEmails.first().emailAddress)
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

    @Test
    fun `Hent signatureAlgorithm og hashFunction`() {
        val cpa = TestUtil.createValidTestCPA()
        val signatureDetails = cpa.getPartyInfoByTypeAndID(partyType = "HER", partyId = "8141253").getCertificateForSignatureValidation(role = "Behandler", service = "BehandlerKrav", action = "OppgjorsMelding")
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", signatureDetails.signatureAlgorithm)
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256", signatureDetails.hashFunction)
    }
}
