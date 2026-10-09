package no.nav.emottak.payload.crypto

import no.nav.emottak.payload.error.SignatureException
import no.nav.emottak.util.retrievePublicX509Certificate
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
        try {
            if (!xmlSignature.checkSignatureValue(xmlSignature.retrievePublicX509Certificate())) {
                throw SignatureException("Invalid Signature!")
            }
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Invalid Signature!", e)
        }
    }
}
