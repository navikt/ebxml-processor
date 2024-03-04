package no.nav.emottak.util.cert

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import no.nav.emottak.util.createCRLFile
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509CRL

class CRLRetriever(private val httpClient: HttpClient) {
    suspend fun updateAllCRLs(): HashMap<X500Name, X509CRL> {
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
                httpClient.request(crlUrl) {
                    method = HttpMethod.Get
                }.body<ByteArray>()
            }
            return createCRLFile(response)
        } catch (e: Exception) {
            throw RuntimeException("$crlUrl: Kunne ikke oppdatere CRL", e)
        }
    }
}
