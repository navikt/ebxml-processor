package no.nav.emottak.payload

import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.payload.configuration.config
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.createX509Certificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SigneringTest {

    @Test
    fun testSigneringAvDokument() {
        val ksm = KeyStoreManager(*config.signering.map { it.resolveKeyStoreConfiguration() }.toTypedArray())
        val signering = PayloadSignering()
        val usignertXMLInputStream = SigneringTest::class.java.classLoader
            .getResourceAsStream("xml/test.xml")
        val usignertDokument = createDocument(usignertXMLInputStream!!)
        assertEquals(0, usignertDokument.getElementsByTagName("Signature").length)

        val signertDokument = signering.signerXML(
            document = usignertDokument,
            createX509Certificate(ksm.getCertificate("nav_virksomhet").encoded)
        )
        assertEquals(1, signertDokument.getElementsByTagName("Signature").length)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            setupEnv()
        }
    }
}
