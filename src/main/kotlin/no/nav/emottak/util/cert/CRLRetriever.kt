package no.nav.emottak.util.cert

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import no.nav.emottak.util.createCRLFile
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509CRL
import java.time.Instant

class CRLRetriever(private val httpClient: HttpClient) {
    suspend fun updateAllCRLs(): List<CRL> {
        val crlFiles = mutableListOf<CRL>()
        log.info("Oppdatering av alle CRLer startet...")
        issuerList.forEach { issuer ->
            log.info("Oppdaterer CRL for <${issuer.key}>")
            val x500Name = X500Name(issuer.key)
            val crlFile = try {
                updateCRL(issuer.value).also {
                    log.info("CRL fra <${issuer.value}> oppdatert")
                }
            } catch (e: Exception) {
                log.warn("Oppdatering av CRL feilet fra <${issuer.value}>", e)
                null
            }
            crlFiles.add(CRL(x500Name, issuer.value, crlFile, Instant.now()))
        }
        return crlFiles.toList()
    }

    suspend fun updateCRL(crlUrl: String): X509CRL {
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
