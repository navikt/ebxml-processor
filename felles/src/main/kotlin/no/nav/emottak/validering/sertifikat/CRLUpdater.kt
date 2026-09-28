package no.nav.emottak.validering.sertifikat

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.emottak.util.createCRLFile
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.security.cert.X509CRL
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CRLUpdater(
    private val httpClient: HttpClient,
    private val crlStore: CRLStore,
    private val refreshInterval: Duration,
    private val issuerList: Map<String, String>
) {
    private val log = LoggerFactory.getLogger(CRLUpdater::class.java)

    init {
        require(!refreshInterval.isZero && !refreshInterval.isNegative) {
            "CRL refresh interval must be positive"
        }
    }

    suspend fun refresh() {
        try {
            crlStore.update(downloadAllCRLs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Oppdatering av CRLer feilet", e)
        }
    }

    fun startIn(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(refreshInterval.toMillis().milliseconds)
            refresh()
        }
    }

    private suspend fun downloadAllCRLs(): List<CRL> {
        log.info("Oppdatering av alle CRLer startet...")
        return coroutineScope {
            issuerList.map { (issuer, url) ->
                async(Dispatchers.IO) { downloadValidatedCRL(X500Name(issuer), url) }
            }.awaitAll()
        }
    }

    private suspend fun downloadValidatedCRL(issuer: X500Name, url: String): CRL {
        log.info("Oppdaterer CRL for <$issuer>")
        val crl = try {
            CRL(issuer, url, downloadCRL(url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Oppdatering av CRL feilet fra <$url>", e)
            return CRL(issuer, url, null)
        }
        return try {
            crl.validate()
            log.info("CRL fra <$url> oppdatert")
            crl
        } catch (e: CertificateValidationException) {
            log.warn("Nedlastet CRL for $issuer er ugyldig", e)
            crl.copy(file = null)
        }
    }

    private suspend fun downloadCRL(url: String): X509CRL =
        createCRLFile(httpClient.get(url).body<ByteArray>())
}
