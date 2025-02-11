package no.nav.emottak.payload.ocspstatus

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.payload.log
import no.nav.emottak.util.getEnvVar
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.RFC4519Style
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
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
import java.math.BigInteger
import java.security.cert.X509Certificate

fun resolveDefaultTruststorePath(): String? {
    return when (getEnvVar("NAIS_CLUSTER_NAME", "lokaltest")) {
        "dev-fss", "prod-fss" -> null
        else -> "truststore.p12" // basically lokal test
    }
}

class SertifikatError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

val ssnPolicyID = ASN1ObjectIdentifier("2.16.578.1.16.3.2")

class OcspStatusService(
    val httpClient: HttpClient,
    val signeringKeyStore: KeyStore,
    val trustStore: KeyStore
) {

    private val bcProvider = BouncyCastleProvider()

    internal fun getCertificateChain(alias: String): Array<X509CertificateHolder> {
        val chain = signeringKeyStore.getCertificateChain(alias)
        return chain?.filterIsInstance<X509Certificate>()?.map { JcaX509CertificateHolder(it) }?.toTypedArray()
            ?: emptyArray()
    }

    private fun getSignerAlias(providerName: String): String {
        val x500Name = X500Name(providerName)
        return certificateAuthorities.caList.firstOrNull {
            it.x500Name == x500Name
        }?.ocspSignerAlias
            ?: throw SertifikatError("Fant ikke sertifikat for signering for issuer DN: $providerName")
    }

    private fun createOCSPRequest(
        certificate: X509Certificate,
        ocspResponderCertificate: X509Certificate
    ): OCSPReq {
        try {
            //   log.debug(Markers.appendEntries(createFieldMap(sertifikatData)), "Sjekker sertifikat")
            val ocspReqBuilder = OCSPReqBuilder()
            val providerName = ocspResponderCertificate.subjectX500Principal.name
            val provider = X500Name(providerName)
            val signerAlias = getSignerAlias(providerName)
            val signerCert: X509Certificate
            try {
                signerCert = signeringKeyStore.getCertificate(signerAlias)
            } catch (e: Exception) {
                throw SertifikatError("Fant ikke signering sertifikat for issuer DN: $providerName med alias $signerAlias")
            }

            val requestorName = signerCert.subjectX500Principal.name

            val digCalcProv = JcaDigestCalculatorProviderBuilder().setProvider(bcProvider).build()
            val id: CertificateID = JcaCertificateID(
                digCalcProv.get(CertificateID.HASH_SHA1),
                ocspResponderCertificate,
                certificate.serialNumber
            )
            ocspReqBuilder.addRequest(id)
            val extensionsGenerator = ExtensionsGenerator()
            /*
            Certificates that have an OCSP service locator will be verified against the OCSP responder.
             */
            getCertificateChain(certificate.issuerX500Principal.name).also {
                extensionsGenerator.addServiceLocator(certificate, provider, it)
            }
            if (!certificate.isVirksomhetssertifikat()) {
                extensionsGenerator.addSsnExtension()
            }
            extensionsGenerator.addNonceExtension()

            ocspReqBuilder.setRequestExtensions(extensionsGenerator.generate())

            ocspReqBuilder.setRequestorName(GeneralName(GeneralName.directoryName, requestorName))
            val request: OCSPReq = ocspReqBuilder.build(
                JcaContentSignerBuilder("SHA256WITHRSAENCRYPTION").setProvider(bcProvider)
                    .build(signeringKeyStore.getKey(signerAlias)),
                signeringKeyStore.getCertificateChain(signerAlias)
            )
            log.debug("OCSP Request created")
            return request
        } catch (e: SertifikatError) {
            throw e
        } catch (e: Exception) {
            log.error("Feil ved opprettelse av OCSP request")
            throw SertifikatError("Feil ved opprettelse av OCSP request", e)
        }
    }

    internal fun ExtensionsGenerator.addNonceExtension() {
        val nonce = BigInteger.valueOf(System.currentTimeMillis())
        this.addExtension(
            OCSPObjectIdentifiers.id_pkix_ocsp_nonce,
            false,
            DEROctetString(nonce.toByteArray())
        )
    }

    private fun getOcspResponderCertificate(certificateIssuer: String): X509Certificate {
        trustStore.aliases().toList().forEach { alias ->
            val cert = trustStore.getCertificate(alias) as X509Certificate
            if (cert.subjectX500Principal.name == certificateIssuer) {
                return cert
            }
        }
        log.warn("Fant ikke issuer sertifikat for '$certificateIssuer', kan ikke gjøre OCSP-spørringer mot denne CAen")
        throw SertifikatError("Fant ikke issuer sertifikat for '$certificateIssuer'")
    }

    private suspend fun postOCSPRequest(url: String, encoded: ByteArray): OCSPResp {
        log.debug("OCSP URL: $url")
        try {
            return withContext(Dispatchers.IO) {
                httpClient.post(url) {
                    setBody(encoded)
                }
            }.let {
                OCSPResp(it.readRawBytes())
            }
        } catch (e: IOException) {
            throw SertifikatError("Feil ved opprettelse av OCSP respons", cause = e)
        } catch (e: Exception) {
            log.error("OCSP feilet ${e.localizedMessage}", e)
            throw SertifikatError("Ukjent feil ved OCSP spørring. Kanskje OCSP endepunktet er nede?")
        }
    }

    suspend fun getOCSPStatus(certificate: X509Certificate): SertifikatInfo {
        return try {
            val certificateIssuer = certificate.issuerX500Principal.name
            // issue av personsertifikaten eller virksomhetsertifikaten (f.ex. Buypass)
            val ocspResponderCertificate = getOcspResponderCertificate(certificateIssuer)
            val request: OCSPReq = createOCSPRequest(certificate, ocspResponderCertificate)

            postOCSPRequest(certificate.getOCSPUrl(), request.encoded).also {
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
            throw SertifikatError(e.localizedMessage, e)
        } catch (e: Exception) {
            throw SertifikatError(e.localizedMessage, e)
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
                    log.error("OCSP response failed to verify")
                    throw SertifikatError("OCSP response failed to verify")
                }
            }
        } catch (e: Exception) {
            log.error("OCSP response validation failed", e)
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
            OCSPResponseStatus.UNAUTHORIZED -> throw SertifikatError(
                "OCSP request UNAUTHORIZED"
            )

            OCSPResponseStatus.SIG_REQUIRED -> throw SertifikatError(
                "OCSP request SIG_REQUIRED"
            )

            OCSPResponseStatus.TRY_LATER -> throw SertifikatError(
                "OCSP request TRY_LATER"
            )

            OCSPResponseStatus.INTERNAL_ERROR -> throw SertifikatError(
                "OCSP request INTERNAL_ERROR"
            )

            OCSPResponseStatus.MALFORMED_REQUEST -> throw SertifikatError(
                "OCSP request MALFORMED_REQUEST"
            )

            OCSPResponseStatus.SUCCESSFUL -> log.info("OCSP Request successful")
        }
    }
}
