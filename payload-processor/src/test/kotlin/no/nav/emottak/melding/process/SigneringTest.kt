package no.nav.emottak.melding.process

import no.nav.emottak.util.createDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SigneringTest {

    @Test
    fun testSigneringAvDokument() {
        val usignertXMLInputStream = SigneringTest::class.java.classLoader
            .getResourceAsStream("xml/test.xml")
        val usignertDokument = createDocument(usignertXMLInputStream!!)
        assertEquals(0, usignertDokument.getElementsByTagName("Signature").length)

        val signertDokument = signer(document = usignertDokument, "emottaktestkeypair")
        assertEquals(1, signertDokument.getElementsByTagName("Signature").length)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup(): Unit {
            setupEnv()
        }
    }
}
