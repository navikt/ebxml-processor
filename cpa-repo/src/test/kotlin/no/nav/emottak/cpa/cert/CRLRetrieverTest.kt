package no.nav.emottak.cpa.cert

import io.kotest.common.runBlocking
import no.nav.emottak.cpa.HttpClientUtil
import no.nav.emottak.cpa.configuration.config
import no.nav.emottak.validering.sertifikat.CRLRetriever
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
            assertContains(list.map { it.x500Name }, X500Name(caEntry.key), "List of CRL should contain entry for ${caEntry.key}")
        }
    }
}
