package no.nav.emottak.validering.sertifikat

import io.kotest.common.runBlocking
import no.nav.emottak.util.HttpClientUtil
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CRLRetrieverTest {
    @Test
    fun `Update all CRLs returns a list of CRLs the same size as CA list`() {
        val crlRetriever = CRLRetriever(HttpClientUtil.client)
        val list = runBlocking {
            crlRetriever.updateAllCRLs()
        }
        assert(defaultCRLLists.isNotEmpty())
        assertEquals(defaultCRLLists.size, list.size)
        defaultCRLLists.forEach { caEntry ->
            assertTrue(list.map { it.x500Name }.contains(X500Name(caEntry.key)), "List of CRL should contain entry for ${caEntry.key}")
        }
    }
}
