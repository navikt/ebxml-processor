package no.nav.emottak.cpa.cert

import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.validation.log
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry
import java.time.Instant
import java.util.Date

val defaultCAList = mapOf(
//    Pair("CN=Buypass Class 3 CA 3,O=Buypass AS-983163327,C=NO","http://crl.buypass.no/crl/BPClass3CA3.crl"),
    Pair("CN=Buypass Class 3 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO", "http://crl.buypassca.com/BPCl3CaG2STBS.crl"),
//    Pair("CN=Buypass Class 3 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2HTPS.crl"),
//    Pair("CN=Buypass Class 3 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2HTBS.crl"),
    Pair("CN=Buypass Class 3 Test4 CA 3, O=Buypass AS-983163327, C=NO", "http://crl.test4.buypass.no/crl/BPClass3T4CA3.crl"),
    Pair("CN=Buypass Class 3 Test4 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO", "http://crl.test4.buypassca.com/BPCl3CaG2STBS.crl")
//    Pair("CN=Buypass Class 3 Test4 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2HTPS.crl"),
//    Pair("CN=Buypass Class 3 Test4 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2HTBS.crl"),
//    Pair("CN=Commfides Legal Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.commfides.com/G3/CommfidesLegalPersonCA-G3.crl"),
//    Pair("CN=Commfides Natural Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.commfides.com/G3/CommfidesNaturalPersonCA-G3.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Enterprise SHA256 CLASS 3","http://crl1.commfides.com/CommfidesEnterprise-SHA256.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Person High SHA256 CLASS 3","http://crl1.commfides.com/CommfidesPerson-High-SHA256.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Enterprise-Norwegian SHA256 CA- TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Enterprise-Norwegian SHA256 CA - TEST","http://crl1.test.commfides.com/CommfidesEnterprise-SHA256.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Person High SHA256 CA - TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Person-High SHA256 CA - TEST","http://crl1.test.commfides.com/CommfidesPerson-High-SHA256.crl"),
//    Pair("CN=Commfides Legal Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.test.commfides.com/G3/CommfidesLegalPersonCA-G3-TEST.crl"),
//    Pair("CN=Commfides Natural Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.test.commfides.com/G3/CommfidesNaturalPersonCA-G3-TEST.crl"),
)

class CRLChecker(
    private val crlRetriever: CRLRetriever,
    private val crlMaximumAgeInSeconds: Long = 3600
) {
    private val crlList: List<CRL> = runBlocking {
        crlRetriever.updateAllCRLs()
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
            validate().let { file!! }
        }
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
    fun validate() {
        when {
            file == null ->
                throw CertificateValidationException("Issuer $x500Name støttet, men henting av CRL har feilet")
            x500Name != X500Name(file!!.issuerX500Principal.name) ->
                throw CertificateValidationException("CRL-fil utstedt av ${file!!.issuerX500Principal.name}, men forventet $x500Name! Dette skal ikke skje!")
        }
    }
}
