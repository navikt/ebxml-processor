package no.nav.emottak.ebms.validation

import no.nav.emottak.ebms.TestData
import no.nav.emottak.message.exception.CertificateValidationException
import no.nav.emottak.message.exception.SignatureValidationException
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.util.decodeBase64
import no.nav.emottak.util.retrievePublicX509Certificate
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.utils.Constants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.w3c.dom.Document
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The signing certificate in the test message is valid from 2022-09-22 to 2025-09-22.
private val clockWhileCertificateIsValid = Clock.fixed(Instant.parse("2023-08-29T12:57:00Z"), ZoneOffset.UTC)
private val clockAfterCertificateExpired = Clock.fixed(Instant.parse("2025-09-23T00:00:00Z"), ZoneOffset.UTC)

class SignaturValidatorTest {

    @Test
    fun `Gyldig signatur med vedlegg valideres`() {
        assertDoesNotThrow {
            SignaturValidator.validate(getSignatureDetailsForTest(), testDocument(), testAttachments(), clockWhileCertificateIsValid)
        }
    }

    @Test
    fun `Signatur uten signerte vedlegg feiler`() {
        assertThrows<SignatureValidationException> {
            SignaturValidator.validate(getSignatureDetailsForTest(), testDocument(), emptyList(), clockWhileCertificateIsValid)
        }
    }

    @Test
    fun `Signert med annet sertifikat enn i CPA feiler`() {
        val exception = assertThrows<SignatureValidationException> {
            SignaturValidator.validate(
                TestData.HarBorgerEgenandel.validSignatureDetails,
                testDocument(),
                testAttachments(),
                clockWhileCertificateIsValid
            )
        }

        assertTrue(exception.message!!.startsWith("Signert med annet sertifikat enn definert i CPA"), exception.message)
    }

    @Test
    fun `Manglende KeyInfo feiler unrecoverable`() {
        val document = testDocument()
        val signature = document.getElementsByTagNameNS(Constants.SignatureSpecNS, Constants._TAG_SIGNATURE).item(0)
        val keyInfo = document.getElementsByTagNameNS(Constants.SignatureSpecNS, Constants._TAG_KEYINFO).item(0)
        signature.removeChild(keyInfo)

        val exception = assertThrows<SignatureValidationException> {
            SignaturValidator.validate(getSignatureDetailsForTest(), document, emptyList(), clockWhileCertificateIsValid)
        }

        assertFalse(exception.isRecoverable())
    }

    @Test
    fun `Utløpt signatursertifikat feiler unrecoverable før CPA-sertifikat og vedlegg leses`() {
        val exception = assertThrows<CertificateValidationException> {
            SignaturValidator.validate(
                getSignatureDetailsForTest().copy(certificate = byteArrayOf()),
                testDocument(),
                emptyList(),
                clockAfterCertificateExpired
            )
        }

        assertFalse(exception.isRecoverable())
        assertTrue(exception.message!!.startsWith("Sertifikat utløpt"), exception.message)
        assertNotNull(exception.x509Certificate)
        assertEquals(
            getSignatureDetailsForTest().retrievePublicX509Certificate(),
            exception.x509Certificate
        )
    }

    private fun testDocument(): Document = getDocumentBuilder().parse(
        this::class.java.classLoader
            .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.xml")
    )

    private fun testAttachments(): List<EbmsAttachment> = listOf(
        EbmsAttachment(
            this::class.java.classLoader
                .getResourceAsStream("oppgjørsmelding/2023_08_29T12_56_58_328.p7m")!!.readAllBytes(),
            "application/pkcs7-mime",
            "3CTGI8UKUKU4.ADHEUDMDCY3Q3@speare.no"
        )
    )
}

fun getSignatureDetailsForTest(): SignatureDetails =
    SignatureDetails(
        certificate = decodeBase64("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".toByteArray()),
        signatureAlgorithm = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
        hashFunction = MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256
    )
