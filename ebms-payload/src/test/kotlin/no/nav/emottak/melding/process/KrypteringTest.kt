package no.nav.emottak.melding.process

import no.nav.emottak.util.crypto.Kryptering
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class KrypteringTest {

    @Test
    fun `Krypter dokument returnerer OK`() {
        val kryptering = Kryptering()
        val inputXML = createInputstreamFromFile("src/test/resources/xml/testfil.xml")
        val sertifikat = createInputstreamFromFile("src/test/resources/keystore/cert.pem")

        val kryptert = kryptering.krypter(inputXML.readBytes(), sertifikat.readBytes())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            setupEnv()
        }
    }
}
