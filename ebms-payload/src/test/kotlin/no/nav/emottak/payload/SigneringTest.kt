package no.nav.emottak.payload

import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.util.createDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SigneringTest {

    @Test
    fun testSigneringAvDokument() {
        val signering = PayloadSignering()
        val usignertXMLInputStream = SigneringTest::class.java.classLoader
            .getResourceAsStream("xml/test.xml")
        val usignertDokument = createDocument(usignertXMLInputStream!!)
        assertEquals(0, usignertDokument.getElementsByTagName("Signature").length)

        val signertDokument = signering.signerXML(document = usignertDokument, signatureDetails())
        assertEquals(1, signertDokument.getElementsByTagName("Signature").length)
    }

    private fun signatureDetails() = SignatureDetails(
        certificate = this::class.java.classLoader.getResourceAsStream("keystore/cert.pem").readAllBytes(),
        signatureAlgorithm = "sha256WithRSAEncryption",
        hashFunction = ""
    )

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            setupEnv()
        }
    }
}
