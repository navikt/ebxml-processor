package no.nav.emottak.payload.helseid.util.util.ocsp

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.RFC4519Style
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPException
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.SingleResp
import org.bouncycastle.cert.ocsp.UnknownStatus
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.IOException
import java.math.BigInteger
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import no.nav.emottak.payload.helseid.util.lang.ByteUtil
import no.nav.emottak.payload.helseid.util.security.SecurityUtils
import no.nav.emottak.payload.helseid.util.security.X509Utils
import no.nav.emottak.payload.helseid.util.security.X509Utils.getSubjectDN
import no.nav.emottak.payload.ocspstatus.RevocationReason
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions", "MaxLineLength")
class OCSPResponseValidator(private val keyStore: KeyStore, private val gracePeriod: Long = DEFAULT_GRACE_PERIOD) {
    private val trustedOcspProviders: Map<X500Name, X509Certificate> = SecurityUtils.getAliases(keyStore).associate {
        val certificate = keyStore.getCertificate(it) as X509Certificate
        X500Name(getSubjectDN(certificate)) to certificate
    }

    companion object {
        // one day grace period, ie. i.e. requestors can cache OCSP responses for 24Hrs
        // and use these in messages timestamped within 24 Hrs of the OCSP response date.
        private const val DEFAULT_GRACE_PERIOD = 3600000 * 24L
        private const val OID_SSN_EXTENSION = "2.16.578.1.16.3.2"
        private val SSN_POLICY_ID = ASN1ObjectIdentifier(OID_SSN_EXTENSION)
        private val PROVIDER: Provider = BouncyCastleProvider()
        private val LOG = LoggerFactory.getLogger("no.nav.emottak.payload.helseid.util.util.ocsp.OCSPResponseValidator")
        private const val HEX_RADIX = 16
        private const val FAILED_TO_MATCH = "Failed to match a CertificateID against a certificate issuer"

        init {
            Security.addProvider(PROVIDER)
        }
    }

    fun validate(response: ByteArray, timestamp: Date): CertificateID {
        val ocspResponse = getOCSPResponse(response)

        // Decode BasicOCSP response (inner structure)
        val basicResponse = getBasicOCSPResponse(ocspResponse)

        // Obtain certificate chain from response
        val ocspCertificates = basicResponse.certs
        LOG.info("number of certificates in the OCSP response: {}", ocspCertificates.size)

        // verify response
        verifyProduced(basicResponse, timestamp)
        verifyOCSPCerts(basicResponse, ocspCertificates)
        val singleResponses = basicResponse.responses
        return validateCertificateStatusFromResponse(singleResponses)
    }

    /**
     * Gets the ssn from the OSCP response from the OSCP list that matches the certificate. Does no validation.
     * @param responses the OSCP responses
     * @param certificate the certificate for the OSCP response we want
     * @return the ssn
     */
    fun getSsn(responses: Collection<String>, certificate: X509Certificate): String? {
        val caCert = trustedOcspProviders[X500Name(X509Utils.getIssuerDN(certificate))]
        return if (caCert != null) {
            val serialNumber = certificate.serialNumber
            getSsn(responses, serialNumber, caCert)
        } else {
            null
        }
    }

    /**
     * Gets the ssn from the OSCP response. Does no validation.
     * @param response the OSCP responses
     * @return the ssn
     */
    fun getSsn(response: String): String {
        val ocspResponse = getOCSPResponse(ByteUtil.decodeBase64(response))
        // Decode BasicOCSP response (inner structure)
        val basicResponse = getBasicOCSPResponse(ocspResponse)
        val singleResp = basicResponse.responses[0]
        return getSsn(basicResponse, singleResp)
    }

    private fun getSsn(
        responses: Collection<String>,
        serialNumber: BigInteger, caCertificate: X509Certificate
    ): String? {
        for (str in responses) {
            val response = ByteUtil.decodeBase64(str)
            val ocspResponse = getOCSPResponse(response)

            // Decode BasicOCSP response (inner structure)
            val basicResponse = getBasicOCSPResponse(ocspResponse)
            val singleResp = basicResponse.responses[0]
            val certId = singleResp.certID
            if (serialNumber == certId.serialNumber && matchesIssuer(certId, caCertificate)) {
                return getSsn(basicResponse, singleResp)
            }
        }
        return null
    }

    private fun getOCSPResponse(response: ByteArray): OCSPResp =
        try {
            val ocspResponse = OCSPResp(response)
            checkOCSPResponseStatus(ocspResponse)
            ocspResponse
        } catch (e: IOException) {
            throw RuntimeException("Failed to parse OCSP response", e)
        }

    private fun getBasicOCSPResponse(ocspResponse: OCSPResp): BasicOCSPResp =
        try {
            ocspResponse.responseObject as BasicOCSPResp?
                ?: throw RuntimeException("No BasicOCSPResp found in response")
        } catch (e: OCSPException) {
            throw RuntimeException("Signature verification failed", e)
        }

    private fun verifyProduced(basicResponse: BasicOCSPResp, timestamp: Date) {
        val signedTime = timestamp.time
        val ocspProduced = basicResponse.producedAt.time
        LOG.debug("OCSP produced: {}, timestamp: {}", basicResponse.producedAt, timestamp)
        if (signedTime > ocspProduced + gracePeriod) {
            throw RuntimeException(
                "Could not trust OCSP response to validate certificates. " +
                        "One or more OCSP responses are dated before signature timestamp. " +
                        "OCSP produced: " + basicResponse.producedAt + ", timestamp: " + timestamp
            )
        }
    }

    private fun verifyOCSPCerts(basicResponse: BasicOCSPResp, ocspCertificates: Array<X509CertificateHolder>) {
        val contentVerifierProviderBuilder = JcaContentVerifierProviderBuilder()
        if (ocspCertificates.isEmpty()) {
            val responderCertificate = getResponderCertificate(basicResponse)
            verifySignature(contentVerifierProviderBuilder, basicResponse, responderCertificate)
        } else {
            if (LOG.isDebugEnabled) {
                LOG.debug("Certificates included in response")
                for (cert in ocspCertificates) {
                    LOG.debug(" {}", cert.subject)
                }
            }
            val certificateHolder = ocspCertificates[0]
            verifyProvider(certificateHolder)
            LOG.info("verifying {}", { certificateHolder.subject.toString() })
            verifySignature(contentVerifierProviderBuilder, basicResponse, certificateHolder)
        }
    }

    private fun getResponderCertificate(basicResponse: BasicOCSPResp): X509Certificate {
        val rid = basicResponse.responderId
        val name = rid.toASN1Primitive().name
        return trustedOcspProviders[name] ?: throw RuntimeException("Untrusted OCSP provider $name")
    }

    private fun verifySignature(
        contentVerifierProviderBuilder: JcaContentVerifierProviderBuilder,
        basicResponse: BasicOCSPResp, certificateHolder: X509CertificateHolder
    ) {
        try {
            verifySignature(contentVerifierProviderBuilder.build(certificateHolder), basicResponse)
        } catch (e: OperatorCreationException) {
            throw RuntimeException(
                "failed to build ContentVerifierProvider for certificate $certificateHolder", e
            )
        } catch (e: CertificateException) {
            throw RuntimeException(
                "failed to build ContentVerifierProvider for certificate $certificateHolder", e
            )
        }
    }

    private fun verifySignature(
        contentVerifierProviderBuilder: JcaContentVerifierProviderBuilder,
        basicResponse: BasicOCSPResp, certificate: X509Certificate
    ) {
        try {
            verifySignature(contentVerifierProviderBuilder.build(certificate), basicResponse)
        } catch (e: OperatorCreationException) {
            throw RuntimeException("failed to build ContentVerifierProvider for certificate $certificate", e)
        }
    }

    private fun verifySignature(contentVerifierProvider: ContentVerifierProvider, basicResponse: BasicOCSPResp) {
        try {
            if (!basicResponse.isSignatureValid(contentVerifierProvider)) {
                LOG.error("verify failed")
                throw RuntimeException("Validation of OCSP response failed. OCSP response signature did not verify.")
            }
        } catch (e: OCSPException) {
            throw RuntimeException(
                "Validation of OCSP response failed. OCSP response signature did not verify.",
                e
            )
        }
    }

    private fun verifyProvider(certificateHolder: X509CertificateHolder): X509Certificate {
        val issuer = certificateHolder.issuer
        for ((key, value) in trustedOcspProviders) {
            if (RFC4519Style.INSTANCE.areEqual(key, issuer)) {
                return value
            }
        }
        throw RuntimeException("OCSP response received from untrusted provider: $issuer")
    }

    /**
     * Checks the status of the OCSP response
     *
     * @param ocspResponse the ocsp response
     */
    @Suppress("ThrowsCount")
    private fun checkOCSPResponseStatus(ocspResponse: OCSPResp) {
        when (ocspResponse.status) {
            OCSPResp.SUCCESSFUL -> { /* OK */
            }

            OCSPResp.INTERNAL_ERROR -> throw RuntimeException("Internal OCSP server error")
            OCSPResp.MALFORMED_REQUEST -> throw RuntimeException("Malformed request")
            OCSPResp.SIG_REQUIRED -> throw RuntimeException("Signature required for request")
            OCSPResp.TRY_LATER -> throw RuntimeException("The server was too busy to answer")
            OCSPResp.UNAUTHORIZED -> throw RuntimeException("Not authorized to access server")
            else -> throw RuntimeException("Unknown OCSPResponse status code")
        }
    }

    /**
     * gets the CertificateID from the SingleResp responses
     *
     * @param singleResponses the single response
     * @return the CertificateID
     */
    private fun validateCertificateStatusFromResponse(singleResponses: Array<SingleResp>): CertificateID {
        if (singleResponses.size != 1) {
            throw RuntimeException("OCSP response included wrong number of status, expected one")
        }
        val singleResponse = singleResponses[0]

        // validate status
        validateCertificateStatus(singleResponse)
        LOG.debug(
            "validated OCSPResponse for certificate with serial number {} (0x{})",
            { singleResponse.certID.serialNumber },
            { singleResponse.certID.serialNumber.toString(HEX_RADIX) })
        return singleResponse.certID
    }

    private fun getSsn(basicResponse: BasicOCSPResp, singleResponse: SingleResp): String {
        var ssn = getSsn(singleResponse.getExtension(SSN_POLICY_ID))
        if (ssn.isEmpty()) {
            ssn = getSsn(basicResponse.getExtension(SSN_POLICY_ID))
            LOG.debug("ssn included in OCSP response: {}", ssn)
        }
        return ssn
    }

    private fun getSsn(ssnExtension: Extension?): String =
        if (ssnExtension != null) {
            try {
                JcaX509ExtensionUtils.parseExtensionValue(ssnExtension.extnValue.encoded).toString()
            } catch (e: IOException) {
                throw RuntimeException("Failed to extract SSN", e)
            }
        } else ""

    @Suppress("ThrowsCount")
    private fun validateCertificateStatus(singleResponse: SingleResp) {
        val certificateStatus = singleResponse.certStatus
        if (certificateStatus !== CertificateStatus.GOOD) {
            when (certificateStatus) {
                is RevokedStatus -> {
                    val reason =
                        if (certificateStatus.hasRevocationReason()) RevocationReason.toString(certificateStatus.revocationReason) else ""
                    LOG.info("certificate is revoked: {}", reason)
                    throw RuntimeException("Certificate is revoked: $reason")
                }

                is UnknownStatus -> {
                    LOG.info("certificate status unknown")
                    throw RuntimeException("Certificate status unknown")
                }

                else -> {
                    LOG.info("can't establish certificate status")
                    throw RuntimeException("Unable to determine certificate, could be revoked")
                }
            }
        }
    }

    /**
     * Checks if this certificateID matches the issuer with issuerCertificate
     * @param certificateID The certificate id.
     * @param issuerCertificate An certificate issuer (CA) certificate
     * @return true if match
     */
    @Suppress("ThrowsCount")
    fun matchesIssuer(certificateID: CertificateID, issuerCertificate: X509Certificate?): Boolean {
        return try {
            certificateID.matchesIssuer(
                JcaX509CertificateHolder(issuerCertificate),
                JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER).build()
            )
        } catch (e: OCSPException) {
            throw RuntimeException(FAILED_TO_MATCH, e)
        } catch (e: CertificateEncodingException) {
            throw RuntimeException(FAILED_TO_MATCH, e)
        } catch (e: OperatorCreationException) {
            throw RuntimeException(FAILED_TO_MATCH, e)
        }
    }

}
