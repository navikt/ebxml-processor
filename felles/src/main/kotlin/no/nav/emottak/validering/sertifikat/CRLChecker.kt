package no.nav.emottak.validering.sertifikat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class CRLChecker(
    private val crlRetriever: CRLRetriever
) {
    private val log = LoggerFactory.getLogger(CRLChecker::class.java)

    private val crlMaximumAgeInSeconds: Long = 3600L

    // Én mutex per issuer sørger for at samtidige requests mot en utdatert/manglende CRL
    // ikke alle utløser parallelle oppdateringer ("thundering herd") mot CRL-utsteder.
    private val updateLocks = ConcurrentHashMap<X500Name, Mutex>()

    private var crlList: List<CRL>? = null
    private val initLock = Mutex()

    private suspend fun getCrlList(): List<CRL> {
        crlList?.let { return it }
        return initLock.withLock {
            crlList ?: crlRetriever.updateAllCRLs().also { crlList = it }
        }
    }

    suspend fun getCRLRevocationInfo(issuer: String, serialNumber: BigInteger) {
        getRevokedCertificate(issuer = X500Name(issuer), serialNumber = serialNumber)?.let {
            throw CertificateValidationException("Sertifikat revokert: serienummer <$serialNumber> revokert med reason <${it.revocationReason}> at <${it.revocationDate}>")
        }
    }

    private suspend fun getRevokedCertificate(issuer: X500Name, serialNumber: BigInteger): X509CRLEntry? {
        return getCRLFile(issuer).getRevokedCertificate(serialNumber)
    }

    private suspend fun getCRLFile(issuer: X500Name): X509CRL {
        val crl = getCrlList().firstOrNull { it.x500Name == issuer }
            ?: throw CertificateValidationException("Issuer $issuer ikke støttet. CRL liste må oppdateres med issuer om denne skal støttes")
        val needsUpdate = with(crl) {
            when {
                file == null -> {
                    log.warn("Issuer $issuer støttet, men CRL er null. Forsøker oppdatering")
                    true
                }
                file!!.nextUpdate?.before(Date.from(Instant.now())) == true -> {
                    log.info("CRL for Issuer $issuer utdatert ${file!!.nextUpdate}. Forsøker oppdatering")
                    true
                }
                updated.isBefore(Instant.now().minusSeconds(crlMaximumAgeInSeconds)) -> {
                    log.info("CRL for Issuer $issuer er eldre enn $crlMaximumAgeInSeconds sekunder. Forsøker oppdatering")
                    true
                }
                else -> false
            }
        }
        if (needsUpdate) {
            updateCRL(crl)
        }
        crl.validate()
        return crl.file!!
    }

    private suspend fun updateCRL(crl: CRL) {
        val lock = updateLocks.computeIfAbsent(crl.x500Name) { Mutex() }
        lock.withLock {
            // Sjekk på nytt inni låsen, i tilfelle en annen coroutine allerede oppdaterte mens vi ventet
            if (crl.file != null && crl.updated.isAfter(Instant.now().minusSeconds(crlMaximumAgeInSeconds)) &&
                crl.file!!.nextUpdate?.after(Date.from(Instant.now())) != false
            ) {
                return
            }
            try {
                crl.file = crlRetriever.updateCRL(crl.url)
                crl.updated = Instant.now()
            } catch (e: Exception) {
                log.warn("Oppdatering av CRL for ${crl.x500Name} feilet!", e)
            }
        }
    }
}

data class CRL(
    val x500Name: X500Name,
    val url: String,
    var file: X509CRL?,
    var updated: Instant = Instant.now()
) {
    fun validate() {
        when {
            file == null ->
                throw CertificateValidationException("Issuer $x500Name støttet, men henting av CRL har feilet")
            x500Name != X500Name(file!!.issuerX500Principal.name) ->
                throw CertificateValidationException("CRL-fil utstedt av ${file!!.issuerX500Principal.name}, men forventet $x500Name! Dette skal ikke skje!")
        }
    }
}
