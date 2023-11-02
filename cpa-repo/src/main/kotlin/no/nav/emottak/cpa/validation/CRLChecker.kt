package no.nav.emottak.cpa.validation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.CpaValidationException
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.Provider
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry

val issuerList = mapOf(
//    Pair("CN=Buypass Class 3 CA 3,O=Buypass AS-983163327,C=NO","http://crl.buypass.no/crl/BPClass3CA3.crl"),
    Pair("CN=Buypass Class 3 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2STBS.crl"),
////    Pair("CN=Buypass Class 3 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2HTPS.crl"),
//    Pair("CN=Buypass Class 3 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.buypassca.com/BPCl3CaG2HTBS.crl"),
//    Pair("CN=Buypass Class 3 Test4 CA 3, O=Buypass AS-983163327, C=NO","http://crl.test4.buypass.no/crl/BPClass3T4CA3.crl"),
    Pair("CN=Buypass Class 3 Test4 CA G2 ST Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2STBS.crl"),
////    Pair("CN=Buypass Class 3 Test4 CA G2 HT Person, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2HTPS.crl"),
//    Pair("CN=Buypass Class 3 Test4 CA G2 HT Business, O=Buypass AS, OID.2.5.4.97=NTRNO-983163327, C=NO","http://crl.test4.buypassca.com/BPCl3CaG2HTBS.crl"),
//    Pair("CN=Commfides Legal Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.commfides.com/G3/CommfidesLegalPersonCA-G3.crl"),
////    Pair("CN=Commfides Natural Person - G3, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.commfides.com/G3/CommfidesNaturalPersonCA-G3.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Enterprise SHA256 CLASS 3","http://crl1.commfides.com/CommfidesEnterprise-SHA256.crl"),
////    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=Commfides Trust Environment (c) 2011 Commfides Norge AS, CN=CPN Person High SHA256 CLASS 3","http://crl1.commfides.com/CommfidesPerson-High-SHA256.crl"),
//    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Enterprise-Norwegian SHA256 CA- TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Enterprise-Norwegian SHA256 CA - TEST","http://crl1.test.commfides.com/CommfidesEnterprise-SHA256.crl"),
////    Pair("C=NO, O=Commfides Norge AS - 988 312 495, OU=CPN Person High SHA256 CA - TEST, OU=Commfides Trust Environment(C) 2014 Commfides Norge AS - TEST, CN=Commfides CPN Person-High SHA256 CA - TEST","http://crl1.test.commfides.com/CommfidesPerson-High-SHA256.crl"),
//    Pair("CN=Commfides Legal Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.test.commfides.com/G3/CommfidesLegalPersonCA-G3-TEST.crl"),
////    Pair("CN=Commfides Natural Person - G3 - TEST, OID.2.5.4.97=NTRNO-988312495, O=Commfides Norge AS, C=NO","https://crl.test.commfides.com/G3/CommfidesNaturalPersonCA-G3-TEST.crl"),
)

val crlChecker = CRLChecker(
    runBlocking {
        CRLHandler(HttpClientUtil()).updateCRLs()
    }
)

class CRLChecker(
    val crlFiles: HashMap<X500Name, X509CRL>
) {

    fun getCRLRevocationInfo(issuer: String, serialNumber: BigInteger) {
        getRevokedCertificate(issuer = X500Name(issuer), serialNumber = serialNumber)?.let {
            throw CpaValidationException("Certificate $serialNumber revoked with reason ${it.revocationReason} at ${it.revocationDate}")
        }
    }

    private fun getRevokedCertificate(issuer: X500Name, serialNumber: BigInteger): X509CRLEntry? {
        val crlFile = crlFiles.get(key = issuer) ?: throw CpaValidationException("Issuer $issuer ikke støttet. CRL liste må oppdateres med issuer om denne skal støttes")
        return crlFile.getRevokedCertificate(serialNumber)
    }
}

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


class HttpClientUtil {

    private val client = HttpClient(CIO) {
        expectSuccess = true
    }

    suspend fun makeHttpRequest(urlString: String): ByteArray {
        val response: HttpResponse = client.request(urlString) {
            method = HttpMethod.Get
        }
        return response.body<ByteArray>()
    }
}
