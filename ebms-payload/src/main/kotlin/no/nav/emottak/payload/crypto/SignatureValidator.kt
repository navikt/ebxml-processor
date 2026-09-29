package no.nav.emottak.payload.crypto

import no.nav.emottak.payload.error.SignatureException
import org.apache.xml.security.Init
import org.apache.xml.security.signature.MissingResourceFailureException
import org.apache.xml.security.signature.XMLSignature

class SignatureValidator {
    init {
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true")
        Init.init()
    }

    @Throws(SignatureException::class)
    fun validate(xmlSignature: XMLSignature) {
        val certificateFromSignature = xmlSignature.keyInfo.x509Certificate

        try {
            if (!xmlSignature.checkSignatureValue(certificateFromSignature) // Regel ID 50)
            ) {
                throw SignatureException("Invalid Signature!")
            }
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Invalid Signature!", e)
        }
    }
}
