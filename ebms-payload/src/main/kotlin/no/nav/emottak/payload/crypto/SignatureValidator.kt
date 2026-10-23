package no.nav.emottak.payload.crypto

import no.nav.emottak.payload.error.SignatureException
import org.apache.xml.security.Init
import org.apache.xml.security.exceptions.XMLSecurityException
import org.apache.xml.security.signature.XMLSignature
import java.security.cert.X509Certificate

class SignatureValidator {
    init {
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true")
        Init.init()
    }

    @Throws(SignatureException::class)
    fun validate(xmlSignature: XMLSignature) {
        val certificateFromSignature = xmlSignature.signerCertificate()

        val valid = try {
            xmlSignature.checkSignatureValue(certificateFromSignature) // Regel ID 50
        } catch (e: XMLSecurityException) {
            throw SignatureException("Invalid Signature!", e)
        }
        if (!valid) throw SignatureException("Invalid Signature!")
    }
}

@Throws(SignatureException::class)
fun XMLSignature.signerCertificate(): X509Certificate = try {
    keyInfo?.x509Certificate
} catch (e: XMLSecurityException) {
    throw SignatureException("Unable to read X509 certificate from signature KeyInfo", e)
} ?: throw SignatureException("Signature does not contain an X509 certificate")
