package no.nav.emottak.validering.sertifikat

import io.kotest.common.runBlocking
import no.nav.emottak.util.HttpClientUtil
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CRLRetrieverTest {

    // Mirrors the test CAs with a crlUrl in ca_list_local.conf. Kept local to felles to avoid a
    // dependency on the modules (cpa-repo/ebms-payload) that load that config, which would create
    // a circular module dependency.
    private val testCaListWithCrl = listOf(
        CertificateAuthority(
            dn = "CN=Buypass Class 3 Test4 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO",
            issuer = "BUYPASS",
            ocspUrl = "http://ocspbs.test4.buypassca.com",
            crlUrl = "http://crl.test4.buypassca.com/BPCl3CaG2STBS.crl"
        ),
        CertificateAuthority(
            dn = "CN=Buypass Class 3 Test4 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO",
            issuer = "BUYPASS",
            ocspUrl = "https://pno.test4.buypassca.com",
            crlUrl = "http://crl.test4.buypassca.com/BPCl3CaG2HTPS.crl"
        ),
        CertificateAuthority(
            dn = "CN=Buypass Class 3 Test4 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO",
            issuer = "BUYPASS",
            ocspUrl = "http://ocspbs.test4.buypassca.com",
            crlUrl = "http://crl.test4.buypassca.com/BPCl3CaG2HTBS.crl"
        ),
        CertificateAuthority(
            dn = "CN=Commfides Legal Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO",
            issuer = "COMMFIDES",
            ocspUrl = "https://ocsp1.test.commfides.com/ocsp",
            crlUrl = "https://crl.test.commfides.com/G3/CommfidesLegalPersonCA-G3-TEST.crl"
        ),
        CertificateAuthority(
            dn = "CN=Commfides Natural Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO",
            issuer = "COMMFIDES",
            ocspUrl = "https://ocsp1.test.commfides.com/ocsp",
            crlUrl = "https://crl.test.commfides.com/G3/CommfidesNaturalPersonCA-G3-TEST.crl"
        )
    )

    @Test
    fun `Update all CRLs returns a list of CRLs the same size as CA list`() {
        val crlLists = testCaListWithCrl.associate { it.dn to it.crlUrl!! }
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
