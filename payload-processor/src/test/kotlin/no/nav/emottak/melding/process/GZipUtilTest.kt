package no.nav.emottak.melding.process

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GZipUtilTest {

    @Test
    fun compress() {
    }

    @Test
    fun uncompress() {
        val komprimertInput = createInputstreamFromFile("src/test/resources/2023_08_29T12_56_58_328.p7m.deenveloped").readBytes()
        assertTrue(isCompressed(komprimertInput))
        val dekomprimert = uncompress(komprimertInput)
        assertFalse(isCompressed(dekomprimert))
        println(String(dekomprimert))
    }
}