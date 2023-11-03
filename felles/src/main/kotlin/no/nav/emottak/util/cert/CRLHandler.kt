package no.nav.emottak.util.cert

import kotlinx.coroutines.runBlocking
import no.nav.emottak.util.HttpClientUtil
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.Provider
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

class CRLHandler(val httpClient: HttpClientUtil) {
    private val provider: Provider = BouncyCastleProvider()
    suspend fun updateCRLs(): HashMap<X500Name, X509CRL> {
        val crlFiles = hashMapOf<X500Name, X509CRL>()
        log.info("Oppdatering av alle CRLer startet...")
        issuerList.forEach { issuer ->
            log.info("Oppdaterer CRL for <${issuer.key}>")
            val x500Name = X500Name(issuer.key)
            try {
                crlFiles[x500Name] = updateCRL(issuer.value)
                log.info("CRL fra <${issuer.value}> oppdatert")
            } catch (e: Exception) {
                log.warn("Oppdatering av CRL feilet fra <${issuer.value}>", e)
            }
        }
        return crlFiles
    }

    private suspend fun updateCRL(crlUrl: String): X509CRL {
        try {
            val response = runBlocking {
                httpClient.makeHttpRequest(crlUrl)
            }
            val factory = CertificateFactory.getInstance("X.509", provider)
            return factory.generateCRL(ByteArrayInputStream(response)) as X509CRL
        } catch (e: Exception) {
            throw RuntimeException("$crlUrl: Kunne ikke oppdatere CRL", e)
        }
    }
}
