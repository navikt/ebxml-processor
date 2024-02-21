package no.nav.emottak.payload

import no.nav.emottak.payload.util.GZipUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GZipUtilTest {

    @Test
    fun compress() {
    }

    @Test
    fun uncompress() {
        val gZipUtil = GZipUtil()
        val komprimertInput = createInputstreamFromFile("src/test/resources/2023_08_29T12_56_58_328.p7m.deenveloped").readBytes()
        assertTrue(gZipUtil.isCompressed(komprimertInput))
        val dekomprimert = gZipUtil.uncompress(komprimertInput)
        assertFalse(gZipUtil.isCompressed(dekomprimert))
        println(String(dekomprimert))
    }
}
