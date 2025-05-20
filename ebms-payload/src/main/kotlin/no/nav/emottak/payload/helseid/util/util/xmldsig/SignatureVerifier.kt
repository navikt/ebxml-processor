package no.nav.emottak.payload.helseid.util.util.xmldsig

import org.w3c.dom.Document
import java.security.cert.X509Certificate

interface SignatureVerifier {

    /**
     * Verifies a signature. throws exception if document does not validate.
     *
     * @param doc The document containing the signature to verify.
     * The outermost signature will be used.
     * @param minimumSignatureAlgorithm the minimum signature algorithm we support
     * @param minimumDigestAlgorithm the minimum digest algorithm we support
     * @return The certificate that was used in the signing.
     */
    fun verifyXML(doc: Document,
                  minimumSignatureAlgorithm: String? = null,
                  minimumDigestAlgorithm: String? = null): X509Certificate
}
