package no.nav.emottak.validering.sertifikat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.crypto.trustStoreConfig
import no.nav.emottak.message.exception.CertificateValidationException
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.Provider
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class CRLChecker(
    private val crlRetriever: CRLRetriever,
    trustStore: KeyStoreManager = KeyStoreManager(trustStoreConfig()),
    private val provider: Provider = BouncyCastleProvider()
) {
    private val log = LoggerFactory.getLogger(CRLChecker::class.java)

    private val crlMaximumAgeInSeconds: Long = 3600L

    private val trustedCertificates: Set<X509Certificate> =
        trustStore.getTrustedRootCerts() + trustStore.getIntermediateCerts()

    // Én mutex per issuer sørger for at samtidige requests mot en utdatert/manglende CRL
    // ikke alle utløser parallelle oppdateringer ("thundering herd") mot CRL-utsteder.
    private val updateLocks = ConcurrentHashMap<X500Name, Mutex>()

    private var crlList: List<CRL>? = null
    private val initLock = Mutex()

    private suspend fun getCrlList(): List<CRL> {
        crlList?.let { return it }
        return initLock.withLock {
            crlList ?: crlRetriever.updateAllCRLs().onEach { crl ->
                crl.file?.let {
                    try {
                        validateCRL(crl, it)
                        crl.validationError = null
                    } catch (e: CertificateValidationException) {
                        // En ugyldig CRL for en issuer skal ikke hindre de andre issuerne i listen fra å
                        // bli tatt i bruk. Den konkrete feilen lagres og kastes på nytt kun når nettopp
                        // denne issueren spørres opp, uten å tvinge frem gjentatt nedlasting av hele listen.
                        log.warn("Validering av CRL for ${crl.x500Name} feilet ved oppstart!", e)
                        crl.validationError = e
                    }
                }
            }.also { crlList = it }
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
        crl.validationError?.let { throw it }
        val file = crl.file
            ?: throw CertificateValidationException("CRL for $issuer ikke tilgjengelig etter oppdatering")
        validateCRL(crl, file)
        return file
    }

    private fun findIssuerCertificate(issuer: X500Name): X509Certificate {
        return trustedCertificates.firstOrNull { X500Name(it.subjectX500Principal.name) == issuer }
            ?: throw CertificateValidationException("Fant ikke CA-sertifikat for issuer $issuer i truststore. Kan ikke verifisere CRL-signatur")
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
                val downloadedCrl = crlRetriever.updateCRL(crl.url)
                validateCRL(crl, downloadedCrl)
                crl.file = downloadedCrl
                crl.updated = Instant.now()
                crl.validationError = null
            } catch (e: Exception) {
                log.warn("Oppdatering av CRL for ${crl.x500Name} feilet!", e)
            }
        }
    }

    private fun validateCRL(crl: CRL, crlFile: X509CRL) {
        crl.validate(crlFile, findIssuerCertificate(crl.x500Name), provider)
        crl.validateValidityWindow(crlFile)
    }
}

data class CRL(
    val x500Name: X500Name,
    val url: String,
    var file: X509CRL?,
    var updated: Instant = Instant.now(),
    var validationError: CertificateValidationException? = null
) {
    /**
     * Validerer at CRL-filen finnes, er utstedt av forventet issuer, er signert av angitt
     * CA-sertifikat, og at CRL-ens gyldighetsvindu (thisUpdate/nextUpdate) er innenfor nå.
     */
    fun validate(crlFile: X509CRL, issuerCertificate: X509Certificate, provider: Provider) {
        if (x500Name != X500Name(crlFile.issuerX500Principal.name)) {
            throw CRLException("CRL-fil utstedt av ${crlFile.issuerX500Principal.name}, men forventet $x500Name! Dette skal ikke skje!")
        }
        try {
            crlFile.verify(issuerCertificate.publicKey, provider.name)
        } catch (e: Exception) {
            throw CRLException("CRL-signatur for $x500Name kunne ikke verifiseres mot CA-sertifikat <${issuerCertificate.subjectX500Principal.name}>", e)
        }
    }

    internal fun validateValidityWindow(crlFile: X509CRL) {
        val now = Date.from(Instant.now())
        if (crlFile.nextUpdate != null && crlFile.nextUpdate.before(now)) {
            throw CRLException("CRL for $x500Name er utløpt (nextUpdate <${crlFile.nextUpdate}>)")
        }
        if (crlFile.thisUpdate.after(now)) {
            throw CRLException("CRL for $x500Name er ikke gyldig enda (thisUpdate <${crlFile.thisUpdate}>)")
        }
    }
}

class CRLException(message: String, cause: Throwable? = null) : Exception(message, cause)
