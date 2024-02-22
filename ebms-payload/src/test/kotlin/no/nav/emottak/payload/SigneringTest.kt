package no.nav.emottak.payload

import no.nav.emottak.util.createDocument
import no.nav.emottak.util.signatur.Signering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SigneringTest {

    @Test
    fun testSigneringAvDokument() {
        val signering = Signering()
        val usignertXMLInputStream = SigneringTest::class.java.classLoader
            .getResourceAsStream("xml/test.xml")
        val usignertDokument = createDocument(usignertXMLInputStream!!)
        assertEquals(0, usignertDokument.getElementsByTagName("Signature").length)

        val signertDokument = signering.signerXML(document = usignertDokument, "emottaktestkeypair")
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
