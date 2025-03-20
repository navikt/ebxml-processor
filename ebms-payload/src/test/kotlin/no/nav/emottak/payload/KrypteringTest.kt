package no.nav.emottak.payload

import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.util.createX509Certificate
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class KrypteringTest {

    @Test
    fun `Krypter dokument returnerer OK`() {
        val kryptering = Kryptering()
        val inputXML = createInputstreamFromFile("src/test/resources/xml/testfil.xml")
        val sertifikat = createInputstreamFromFile("src/test/resources/keystore/cert.pem")

        val kryptert = kryptering.krypter(inputXML.readBytes(), createX509Certificate(sertifikat.readBytes()))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            setupEnv()
        }
    }
}
