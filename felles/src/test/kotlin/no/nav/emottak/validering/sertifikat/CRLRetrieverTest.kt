package no.nav.emottak.validering.sertifikat

import io.kotest.common.runBlocking
import no.nav.emottak.util.HttpClientUtil
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.validering.sertifikat.CRLRetriever
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CRLRetrieverTest {
    @Test
    fun `Update all CRLs returns a list of CRLs the same size as CA list`() {
        val crlLists = config.caList.filter { it.crlUrl != null }.associate { it.dn to it.crlUrl!! }
        val crlRetriever = CRLRetriever(HttpClientUtil.client, crlLists)
        val list = runBlocking {
            crlRetriever.updateAllCRLs()
        }
        assert(crlLists.isNotEmpty())
        assertEquals(crlLists.size, list.size)
        crlLists.forEach { caEntry ->
            assertTrue(list.map { it.x500Name }.contains(X500Name(caEntry.key)), "List of CRL should contain entry for ${caEntry.key}")
        }
    }
}
