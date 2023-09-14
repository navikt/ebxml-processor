package no.nav.emottak.melding.process

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class KrypteringTest {

    @Test
    fun `Krypter dokument returnerer OK`() {
        val inputXML = createInputstreamFromFile("src/test/resources/xml/testfil.xml")
        val sertifikat = createInputstreamFromFile("src/test/resources/keystore/cert.pem")

        val kryptert = krypter(inputXML.readBytes(), sertifikat.readBytes())


    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup(): Unit {
            setupEnv()
        }
    }
}