package no.nav.emottak.cpa.cert

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.emottak.cpa.validation.log
import no.nav.emottak.util.createCRLFile
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509CRL

class CRLRetriever(
    private val httpClient: HttpClient,
    private val issuerList: Map<String, String> = defaultCAList
) {
    suspend fun updateAllCRLs(): List<CRL> {
        log.info("Oppdatering av alle CRLer startet...")
        return mutableListOf<Deferred<CRL>>().also { list ->
            coroutineScope {
                issuerList.forEach { issuer ->
                    list.add(
                        async(Dispatchers.IO) {
                            log.info("Oppdaterer CRL for <${issuer.key}>")
                            val x500Name = X500Name(issuer.key)
                            val crlFile = try {
                                updateCRL(issuer.value)
                            } catch (e: Exception) {
                                log.warn("Oppdatering av CRL feilet fra <${issuer.value}>", e)
                                null
                            }
                            CRL(x500Name, issuer.value, crlFile)
                        }
                    )
                }
            }
        }.awaitAll().toList()
    }

    suspend fun updateCRL(crlUrl: String): X509CRL {
        return try {
            createCRLFile(
                httpClient.get(crlUrl).body<ByteArray>()
            ).also {
                log.info("CRL fra <$crlUrl> oppdatert")
            }
        } catch (e: Exception) {
            throw RuntimeException("$crlUrl: Kunne ikke oppdatere CRL", e)
        }
    }
}
