package no.nav.emottak.cpa.cert

import io.kotest.common.runBlocking
import no.nav.emottak.cpa.HttpClientUtil
import org.bouncycastle.asn1.x500.X500Name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CRLRetrieverTest {
    @Test
    fun `Update all CRLs returns a list of CRLs the same size as CA list`() {
        val crlRetriever = CRLRetriever(HttpClientUtil.client)
        val list = runBlocking {
            crlRetriever.updateAllCRLs()
        }
        assert(defaultCAList.isNotEmpty())
        assertEquals(defaultCAList.size, list.size)
        defaultCAList.forEach { caEntry ->
            assertContains(list.map { it.x500Name }, X500Name(caEntry.key), "List of CRL should contain entry for ${caEntry.key}")
        }
    }
}
