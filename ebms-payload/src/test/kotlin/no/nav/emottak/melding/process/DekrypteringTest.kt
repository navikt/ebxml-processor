package no.nav.emottak.melding.process

import no.nav.emottak.util.crypto.Dekryptering
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DekrypteringTest {

    @Test
    fun dekrypterFil() {
        val dekryptering = Dekryptering()
        val input = createInputstreamFromFile("src/test/resources/xml/kryptert_fil")
        val dekryptert = dekryptering.dekrypter(input.readBytes(), isBase64 = true)

        val expectedOutput = createInputstreamFromFile("src/test/resources/xml/testfil.xml").readBytes()

        assertTrue(expectedOutput.contentEquals(dekryptert.also { println("dekryptert" + String(dekryptert)) }).also { println("expected" + String(expectedOutput))  })

    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup(): Unit {
            setupEnv()
        }
    }
}

