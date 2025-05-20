package no.nav.emottak.payload.helseid.util.util.xmldsig

import java.security.cert.X509Certificate
import no.nav.emottak.payload.helseid.util.security.X509Utils.getIssuerDN
import no.nav.emottak.payload.helseid.util.security.X509Utils.getSubjectDN
import no.nav.emottak.payload.helseid.util.security.X509Utils.isSelfSigned
import org.apache.xml.security.Init
import org.apache.xml.security.algorithms.MessageDigestAlgorithm
import org.apache.xml.security.exceptions.XMLSecurityException
import org.apache.xml.security.keys.KeyInfo
import org.apache.xml.security.keys.keyresolver.KeyResolverException
import org.apache.xml.security.signature.SignedInfo
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.signature.XMLSignatureException
import org.apache.xml.security.utils.Constants
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

@Suppress("TooManyFunctions")
class XMLDSIGVerifier : SignatureVerifier {

    companion object {
        private val LOG = LoggerFactory.getLogger(XMLDSIGVerifier::class.java)
        private const val FAILED_TO_GET_SIGNATURES = "Failed to get signatures"

        /**
         * gets the last certificate in a chain
         * @param chain the certificate chain
         * @return the last certificate
         */
        private fun getLastCertificateInChain(chain: List<X509Certificate>): X509Certificate {
            return chain[chain.size - 1]
        }

        init {
            Init.init()
        }
    }

    private val signatureAlgorithmValidator = AlgorithmValidator(
        listOf(
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1,
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA224,
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA384,
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA512
        )
    )
    private val digestAlgorithmValidator = AlgorithmValidator(
        listOf(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1,
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA224,
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256,
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA384,
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA512
        )
    )

    /**
     * Verifies a signature. throws exception if document does not validate.
     *
     * @param doc The document containing the signature to verify.
     * The outermost signature will be used.
     * @return The certificate that was used in the signing.
     */
    fun verifyXML(doc: Document): X509Certificate =
        try {
            val signature = XMLSignature(getSignatureElement(doc), Constants.SignatureSpecNS)
            verify(signature, null, null)
        } catch (e: XMLSecurityException) {
            throw RuntimeException(FAILED_TO_GET_SIGNATURES, e)
        }

    /**
     * Verifies a signature. throws exception if document does not validate.
     *
     * @param doc The document containing the signature to verify.
     * The outermost signature will be used.
     * @param minimumSignatureAlgorithm the minimum signature algorithm we support
     * @param minimumDigestAlgorithm the minimum digest algorithm we support
     * @return The certificate that was used in the signing.
     */
    override fun verifyXML(
        doc: Document,
        minimumSignatureAlgorithm: String?, minimumDigestAlgorithm: String?
    ): X509Certificate =
        try {
            val signature = XMLSignature(getSignatureElement(doc), Constants.SignatureSpecNS)
            verify(signature, minimumSignatureAlgorithm, minimumDigestAlgorithm)
        } catch (e: XMLSecurityException) {
            throw RuntimeException(FAILED_TO_GET_SIGNATURES, e)
        }

    /**
     * Verifies a signature. throws exception if document does not validate.
     *
     * @param element The element containing a signature to verify.
     * @return The certificate that was used in the signing.
     */
    fun verifyXML(element: Element): X509Certificate =
        try {
            val signature = XMLSignature(element, Constants.SignatureSpecNS)
            verify(signature, null, null)
        } catch (e: XMLSecurityException) {
            throw RuntimeException(FAILED_TO_GET_SIGNATURES, e)
        }

    /**
     * Verifies a signature. throws exception if document does not validate.
     *
     * @param element The element containing a signature to verify.
     * @param minimumSignatureAlgorithm the minimum signature algorithm we support
     * @param minimumDigestAlgorithm the minimum digest algorithm we support
     * @return The certificate that was used in the signing.
     */
    fun verifyXML(
        element: Element,
        minimumSignatureAlgorithm: String?, minimumDigestAlgorithm: String?
    ): X509Certificate =
        try {
            val signature = XMLSignature(element, Constants.SignatureSpecNS)
            verify(signature, minimumSignatureAlgorithm, minimumDigestAlgorithm)
        } catch (e: XMLSecurityException) {
            throw RuntimeException(FAILED_TO_GET_SIGNATURES, e)
        }

    /**
     * Verifies a signature. throws exception if document does not validate.
     *
     * @param signature The signature to verify.
     * @param minimumSignatureAlgorithm the minimum signature algorithm we support
     * @param minimumDigestAlgorithm the minimum digest algorithm we support
     * @return The certificate that was used in the signing.
     */
    @Suppress("ThrowsCount")
    private fun verify(
        signature: XMLSignature,
        minimumSignatureAlgorithm: String?, minimumDigestAlgorithm: String?
    ): X509Certificate {
        val signers = StringBuilder()
        val ki = getKeyInfo(signature)
        try {
            val chain = getCertificateChain(ki)
            if (chain.isEmpty()) {
                verifyNoChain(ki, signature)
            } else {
                val certificate = getLastCertificateInChain(chain)
                val subject = getSubjectDN(certificate)
                if (LOG.isDebugEnabled) {
                    LOG.debug("subject: {}", subject)
                }
                signers.append(subject)
                if (signature.checkSignatureValue(certificate)) {
                    validateAlgorithms(signature.signedInfo, minimumSignatureAlgorithm, minimumDigestAlgorithm)
                    return certificate
                }
            }
        } catch (ex: XMLSignatureException) {
            throw RuntimeException("Failed to extract Signature element", ex)
        } catch (ex: XMLSecurityException) {
            throw RuntimeException("Failed to verify signature", ex)
        }
        throw signatureNotVerfied(signature, signers)
    }

    @Throws(XMLSecurityException::class)
    private fun validateAlgorithms(
        signedInfo: SignedInfo,
        minimumSignatureAlgorithm: String?,
        minimumDigestAlgorithm: String?
    ) {
        if (minimumSignatureAlgorithm != null) {
            val algorithm = signedInfo.signatureAlgorithm.uri
            signatureAlgorithmValidator.validateMinimum(algorithm, minimumSignatureAlgorithm, "signature")
        }
        if (minimumDigestAlgorithm != null) {
            for (i in 0 until signedInfo.length) {
                val ref = signedInfo.item(i)
                val algorithm = ref.messageDigestAlgorithm.algorithmURI
                digestAlgorithmValidator.validateMinimum(algorithm, minimumDigestAlgorithm, "message digest")
            }
        }
    }

    private fun getKeyInfo(signature: XMLSignature): KeyInfo {
        return signature.keyInfo ?: throw RuntimeException("The document does not contain KeyInfo")
    }

    @Throws(XMLSignatureException::class, KeyResolverException::class)
    private fun verifyNoChain(ki: KeyInfo, signature: XMLSignature): Boolean {
        LOG.debug("did not find a certificate chain")
        val pk = ki.publicKey
        if (pk != null) {
            return signature.checkSignatureValue(pk)
        }
        throw RuntimeException("The document does not contain a certificate or a public key")
    }

    /**
     * gets the certificate chain from the KeyInfo
     * @param ki the KeyInfo.
     * @return the certificate chain.
     * @throws XMLSecurityException if we can't get the certificate
     */
    private fun getCertificateChain(ki: KeyInfo): List<X509Certificate> {
        val certificates = getCertificates(ki)
        var issuer: String? = null
        val range = 0 until certificates.size
        val chain = range.map {
            val tmp = getNextCertificateInChain(issuer, certificates)
            issuer = getSubjectDN(tmp)
            tmp
        }
        if (certificates.isNotEmpty()) {
            // the list should now be empty
            throw RuntimeException("Illegal certificate chain i signature")
        }
        return chain
    }

    /**
     * gets the certificates from the KeyInfo
     * @param ki the KeyInfo.
     * @return The certificates in the KeyInfo
     * @throws XMLSecurityException if we can't get the certificate
     */
    private fun getCertificates(ki: KeyInfo): MutableList<X509Certificate> {
        val certificates: MutableList<X509Certificate> = mutableListOf()
        if (ki.containsX509Data()) {
            addCertificatesX509Data(ki, certificates)
        } else {
            addCertificate(ki, certificates)
        }
        return certificates
    }

    private fun addCertificatesX509Data(ki: KeyInfo, certificates: MutableList<X509Certificate>) {
        if (LOG.isDebugEnabled) {
            LOG.debug("found X509Data element in the KeyInfo")
        }
        if (ki.x509Certificate == null) {
            throw RuntimeException("Failed to find signer certificate")
        }
        val noX509Data = ki.lengthX509Data()
        for (i in 0 until noX509Data) {
            val x509Data = ki.itemX509Data(i)
            val noCerts = x509Data.lengthCertificate()
            for (j in 0 until noCerts) {
                certificates.add(x509Data.itemCertificate(j).x509Certificate)
            }
        }
    }

    private fun addCertificate(ki: KeyInfo, certificates: MutableList<X509Certificate>) {
        val cert = ki.x509Certificate
        if (cert != null) {
            certificates.add(cert)
        }
    }

    /**
     * gets the next certificate in the chain
     * @param issuer the issuer of the next certificate
     * @param list the list of certificates. if a certificate is found it is removed from the list.
     * @return the next certificate in the chain
     */
    private fun getNextCertificateInChain(issuer: String?, list: MutableList<X509Certificate>): X509Certificate {
        return if (issuer == null) {
            val func: (X509Certificate) -> Boolean = { isSelfSigned(it) }
            var c = findCertificateAndRemoveFromList(list, func)
            if (c == null) {
                c = findCertificateWithIssuerCertMissing(list)
            }
            if (c != null) {
                return c
            }
            throw RuntimeException("Illegal certificate chain in signature")
        } else {
            // find the certificate that is signed by the issuer
            findCertificateSignedByIssuer(issuer, list)
        }
    }

    private fun findCertificateWithIssuerCertMissing(list: MutableList<X509Certificate>): X509Certificate? {
        val func: (X509Certificate) -> Boolean = { !isAnyCertificatesInListSignedBy(getSubjectDN(it), list) }
        return findCertificateAndRemoveFromList(list, func)
    }

    private fun findCertificateSignedByIssuer(issuer: String, list: MutableList<X509Certificate>): X509Certificate {
        val func: (X509Certificate) -> Boolean = { issuer == getIssuerDN(it) }
        val certificate = findCertificateAndRemoveFromList(list, func)
        if (certificate != null) {
            return certificate
        }
        throw RuntimeException("Illegal certificate chain in signature")
    }

    private fun findCertificateAndRemoveFromList(
        list: MutableList<X509Certificate>,
        predicate: (X509Certificate) -> Boolean
    ): X509Certificate? {
        val c = list.firstOrNull { predicate(it) }
        list.remove(c) // ok to remove null here
        return c
    }

    /**
     * checks if any certificates in the list is issued by this issuer
     * @param issuer the issuer
     * @param list the list of certificates
     * @return true if any certificates in the list is issued by this issuer
     */
    private fun isAnyCertificatesInListSignedBy(issuer: String, list: List<X509Certificate>): Boolean =
        list.any { issuer == getIssuerDN(it) }

    private fun signatureNotVerfied(signature: XMLSignature, signers: StringBuilder): RuntimeException {
        val msg = StringBuilder("Invalid signature. ")
        val failedReferences = getFailedReferenceURIs(signature)
        if (failedReferences.isNotEmpty()) {
            msg.append("Validation of signature references failed. ")
            failedReferences.forEach {
                msg.append("Validation of signature reference with URI=").append(it).append(" failed. ")
            }
            msg.append("The document can have been changed after signing. ")
        } else {
            msg.append("Validation of signature value failed. ")
        }
        if (signers.toString().isBlank()) {
            msg.append("failed to extract document signer.")
        } else {
            msg.append("Signed by: ").append(signers.toString())
        }
        return RuntimeException(msg.toString())
    }

    @Suppress("NestedBlockDepth")
    private fun getFailedReferenceURIs(signature: XMLSignature): List<String> {
        val signedInfo = signature.signedInfo
        return try {
            (0 until signedInfo.length)
                .filter { !signedInfo.getVerificationResult(it) }
                .map { if ("" == signedInfo.item(it).uri) "\"\"" else signedInfo.item(it).uri }
        } catch (e: XMLSecurityException) {
            throw RuntimeException("Failed to get failed reference URIs", e)
        }
    }

    /**
     * Gets the Signature element from the document.
     * @param doc The XML document.
     * @return The signature element.
     */
    private fun getSignatureElement(doc: Document): Element {
        return doc.getElementsByTagNameNS(Constants.SignatureSpecNS, Constants._TAG_SIGNATURE).item(0) as Element
    }
}