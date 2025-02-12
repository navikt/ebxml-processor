package no.nav.emottak.payload.ocspstatus

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.payload.config
import no.nav.emottak.payload.log
import no.nav.emottak.util.getEnvVar
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.RFC4519Style
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.OCSPException
import org.bouncycastle.cert.ocsp.OCSPReq
import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.IOException
import java.security.cert.X509Certificate

fun resolveDefaultTruststorePath(): String? {
    return when (getEnvVar("NAIS_CLUSTER_NAME", "lokaltest")) {
        "dev-fss", "prod-fss" -> null
        else -> "keystore/test_truststore2024.p12" // basically lokal test
    }
}

class SertifikatError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class OcspStatusService(
    val httpClient: HttpClient,
    val signingKeyStoreManager: KeyStoreManager = KeyStoreManager(
        ocspSigneringConfigCommfides(),
        ocspSigneringConfigBuypass()
    ),
    private val trustStore: KeyStoreManager = KeyStoreManager(trustStoreConfig())
) {

    private val bcProvider = BouncyCastleProvider()

    private fun createOCSPRequest(
        certificateFromSignature: X509Certificate,
        ocspResponderCertificate: X509Certificate
    ): OCSPReq {
        try {
            //   log.debug(Markers.appendEntries(createFieldMap(sertifikatData)), "Sjekker sertifikat")
            val ocspReqBuilder = OCSPReqBuilder()
            val requestorName = certificateFromSignature.subjectX500Principal.name

            val digCalcProv = JcaDigestCalculatorProviderBuilder().setProvider(bcProvider).build()
            ocspReqBuilder.addRequest(
                JcaCertificateID(
                    digCalcProv.get(CertificateID.HASH_SHA1),
                    ocspResponderCertificate,
                    certificateFromSignature.serialNumber
                )
            )
            val extensionsGenerator = ExtensionsGenerator()
            /*
            Certificates that have an OCSP service locator will be verified against the OCSP responder.
             */
            val providerName = ocspResponderCertificate.subjectX500Principal.name
            signingKeyStoreManager.getCertificateChain(certificateFromSignature.issuerX500Principal.name).also {
                extensionsGenerator.addServiceLocator(certificateFromSignature, X500Name(providerName), it)
            }
            if (!certificateFromSignature.isVirksomhetssertifikat()) {
                extensionsGenerator.addSsnExtension()
            }
            extensionsGenerator.addNonceExtension()
            ocspReqBuilder.setRequestExtensions(extensionsGenerator.generate())
            ocspReqBuilder.setRequestorName(GeneralName(GeneralName.directoryName, requestorName))

            return ocspReqBuilder.build( // TODO Feiler her fordi feil signer-alias hentes ut man må hente nav sitt signer alias
                JcaContentSignerBuilder("SHA256WITHRSAENCRYPTION").setProvider(bcProvider)
                    .build(signingKeyStoreManager.getKeyForIssuer(ocspResponderCertificate.issuerX500Principal)),
                signingKeyStoreManager.getCertificateChain(signingKeyStoreManager.getCertificateAlias(ocspResponderCertificate))
            ).also {
                log.debug("OCSP Request created")
            }
        } catch (e: Exception) {
            throw SertifikatError("Feil ved opprettelse av OCSP request", e)
        }
    }

    private fun getOcspResponderCertificate(certificateIssuer: String): X509Certificate {
        trustStore.aliases().toList().forEach { alias ->
            with(trustStore.getCertificate(alias)) {
                if (this.subjectX500Principal.name == certificateIssuer) {
                    return this
                }
            }
        }
        log.warn("Fant ikke issuer sertifikat for '$certificateIssuer', kan ikke gjøre OCSP-spørringer mot denne CAen")
        throw SertifikatError("Fant ikke issuer sertifikat for '$certificateIssuer'")
    }

    suspend fun postOCSPRequest(url: String, encoded: ByteArray): OCSPResp {
        log.debug("OCSP URL: $url")
        try {
            return withContext(Dispatchers.IO) {
                httpClient.post(url) {
                    setBody(encoded)
                }
            }.let {
                OCSPResp(it.readBytes())
            }
        } catch (e: IOException) {
            throw SertifikatError("Feil ved opprettelse av OCSP respons", cause = e)
        } catch (e: Exception) {
            throw SertifikatError("Ukjent feil ved OCSP spørring. Kanskje OCSP endepunktet er nede?", e)
        }
    }

    suspend fun getOCSPStatus(certificate: X509Certificate): SertifikatInfo {
        return try {
            val certificateIssuer = certificate.issuerX500Principal.name
            val ocspResponderCertificate = getOcspResponderCertificate(certificateIssuer)
            val request: OCSPReq = createOCSPRequest(certificate, ocspResponderCertificate)

            val ocspUrl = config().caList.firstOrNull {
                X500Name(it.dn) == X500Name(ocspResponderCertificate.subjectX500Principal.name)
            }?.ocspUrl ?: throw SertifikatError("${ocspResponderCertificate.subjectX500Principal.name} not found in CA-list config.")

            postOCSPRequest(ocspUrl, request.encoded).also {
                validateOcspResponse(
                    it,
                    request.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce),
                    ocspResponderCertificate
                )
            }.let {
                it.responseObject as BasicOCSPResp
            }.let {
                val ssn = getSSN(it)
                createSertifikatInfoFromOCSPResponse(certificate, it.responses[0], ssn)
            }
        } catch (e: SertifikatError) {
            throw SertifikatError(e.message ?: "Sertifikatsjekk feilet", e)
        } catch (e: Exception) {
            throw SertifikatError(e.message ?: "Sertifikatsjekk feilet", e)
        }
    }

    private fun validateOcspResponse(
        response: OCSPResp,
        requestNonce: Extension,
        ocspResponderCertificate: X509Certificate
    ) {
        checkOCSPResponseStatus(response.status)
        val basicOCSPResponse: BasicOCSPResp = getBasicOCSPResp(response)
        verifyNonce(requestNonce, basicOCSPResponse.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce))
        val ocspCertificates = basicOCSPResponse.certs
        verifyOCSPCerts(basicOCSPResponse, ocspCertificates, ocspResponderCertificate)
        if (basicOCSPResponse.responses.size == 1) {
            basicOCSPResponse.responses[0]
        } else {
            throw SertifikatError("OCSP response included wrong number of status, expected one")
        }
    }

    private fun verifyOCSPCerts(
        basicOCSPResponse: BasicOCSPResp,
        certificates: Array<X509CertificateHolder>,
        ocspResponderCertificate: X509Certificate
    ) {
        val contentVerifierProviderBuilder = JcaContentVerifierProviderBuilder()
        try {
            if (certificates.isEmpty()) {
                if (!basicOCSPResponse.isSignatureValid(contentVerifierProviderBuilder.build(ocspResponderCertificate))) {
                    throw RuntimeException("OCSP response failed to verify")
                }
            } else {
                val cert = certificates[0]
                verifyProvider(cert, X500Name(ocspResponderCertificate.subjectX500Principal.name))
                if (!basicOCSPResponse.isSignatureValid(contentVerifierProviderBuilder.build(cert))) {
                    throw SertifikatError("OCSP response failed to verify")
                }
            }
        } catch (e: Exception) {
            throw SertifikatError("OCSP response validation failed", cause = e)
        }
    }

    private fun verifyProvider(cert: X509CertificateHolder, provider: X500Name) {
        if (!RFC4519Style.INSTANCE.areEqual(provider, cert.issuer)) {
            throw SertifikatError("OCSP response received from unexpected provider: ${cert.issuer}")
        }
    }

    private fun getBasicOCSPResp(ocspresp: OCSPResp): BasicOCSPResp {
        return try {
            ocspresp.responseObject as BasicOCSPResp
        } catch (e: OCSPException) {
            throw SertifikatError("Feil ved opprettelse av OCSP respons", cause = e)
        }
    }

    private fun verifyNonce(requestNonce: Extension, responseNonce: Extension) {
        if (requestNonce != responseNonce) {
            throw SertifikatError("OCSP response nonce failed to validate")
        }
    }

    private fun checkOCSPResponseStatus(responseStatus: Int) {
        when (responseStatus) {
            OCSPResponseStatus.SUCCESSFUL -> log.info("OCSP Request successful")
            else -> {
                throw SertifikatError("OCSP request failed with status ${OCSPResponseStatus.getInstance(responseStatus)}")
            }
        }
    }
}
