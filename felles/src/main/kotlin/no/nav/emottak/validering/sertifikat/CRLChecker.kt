package no.nav.emottak.validering.sertifikat

import kotlinx.coroutines.runBlocking
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.crypto.trustStoreConfig
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

class CRLChecker(
    private val crlRetriever: CRLRetriever,
    trustStore: KeyStoreManager = KeyStoreManager(trustStoreConfig()),
    private val provider: Provider = BouncyCastleProvider()
) {
    private val log = LoggerFactory.getLogger(CRLChecker::class.java)

    private val crlMaximumAgeInSeconds: Long = 3600L

    private val trustedCertificates: Set<X509Certificate> =
        trustStore.getTrustedRootCerts() + trustStore.getIntermediateCerts()

    private val crlList: List<CRL> by lazy {
        runBlocking {
            crlRetriever.updateAllCRLs()
        }
    }

    fun getCRLRevocationInfo(issuer: String, serialNumber: BigInteger) {
        getRevokedCertificate(issuer = X500Name(issuer), serialNumber = serialNumber)?.let {
            throw CertificateValidationException("Sertifikat revokert: serienummer <$serialNumber> revokert med reason <${it.revocationReason}> at <${it.revocationDate}>")
        }
    }

    private fun getRevokedCertificate(issuer: X500Name, serialNumber: BigInteger): X509CRLEntry? {
        return getCRLFile(issuer).getRevokedCertificate(serialNumber)
    }

    private fun getCRLFile(issuer: X500Name): X509CRL {
        val crl = crlList.firstOrNull { it.x500Name == issuer }
            ?: throw CertificateValidationException("Issuer $issuer ikke støttet. CRL liste må oppdateres med issuer om denne skal støttes")
        return with(crl) {
            when {
                file == null -> {
                    log.warn("Issuer $issuer støttet, men CRL er null. Forsøker oppdatering")
                    updateCRL(this)
                }
                file!!.nextUpdate?.before(Date.from(Instant.now())) == true -> {
                    log.info("CRL for Issuer $issuer utdatert ${file!!.nextUpdate}. Forsøker oppdatering")
                    updateCRL(this)
                }
                updated.isBefore(Instant.now().minusSeconds(crlMaximumAgeInSeconds)) -> {
                    log.info("CRL for Issuer $issuer er eldre enn $crlMaximumAgeInSeconds sekunder. Forsøker oppdatering")
                    updateCRL(this)
                }
            }
            validate(findIssuerCertificate(issuer), provider).let { file!! }
        }
    }

    private fun findIssuerCertificate(issuer: X500Name): X509Certificate {
        return trustedCertificates.firstOrNull { X500Name(it.subjectX500Principal.name) == issuer }
            ?: throw CertificateValidationException("Fant ikke CA-sertifikat for issuer $issuer i truststore. Kan ikke verifisere CRL-signatur")
    }

    private fun updateCRL(crl: CRL) {
        try {
            crl.file = runBlocking {
                crlRetriever.updateCRL(crl.url)
            }
            crl.updated = Instant.now()
        } catch (e: Exception) {
            log.warn("Oppdatering av CRL for ${crl.x500Name} feilet!", e)
        }
    }
}

data class CRL(
    val x500Name: X500Name,
    val url: String,
    var file: X509CRL?,
    var updated: Instant = Instant.now()
) {
    /**
     * Validerer at CRL-filen finnes, er utstedt av forventet issuer, er signert av angitt
     * CA-sertifikat, og at CRL-ens gyldighetsvindu (thisUpdate/nextUpdate) er innenfor nå.
     */
    fun validate(issuerCertificate: X509Certificate, provider: Provider) {
        val crlFile = file
            ?: throw CertificateValidationException("Issuer $x500Name støttet, men henting av CRL har feilet")
        if (x500Name != X500Name(crlFile.issuerX500Principal.name)) {
            throw CertificateValidationException("CRL-fil utstedt av ${crlFile.issuerX500Principal.name}, men forventet $x500Name! Dette skal ikke skje!")
        }
        try {
            crlFile.verify(issuerCertificate.publicKey, provider.name)
        } catch (e: Exception) {
            throw CertificateValidationException("CRL-signatur for $x500Name kunne ikke verifiseres mot CA-sertifikat <${issuerCertificate.subjectX500Principal.name}>", e)
        }
        val now = Date.from(Instant.now())
        if (crlFile.nextUpdate != null && crlFile.nextUpdate.before(now)) {
            throw CertificateValidationException("CRL for $x500Name er utløpt (nextUpdate <${crlFile.nextUpdate}>)")
        }
        if (crlFile.thisUpdate.after(now)) {
            throw CertificateValidationException("CRL for $x500Name er ikke gyldig enda (thisUpdate <${crlFile.thisUpdate}>)")
        }
    }
}
