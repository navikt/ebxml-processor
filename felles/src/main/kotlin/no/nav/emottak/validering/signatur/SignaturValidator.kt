package no.nav.emottak.validering.signatur

import no.nav.emottak.util.retrieveSignatureElement
import org.apache.xml.security.Init
import org.apache.xml.security.signature.MissingResourceFailureException
import org.w3c.dom.Document

class SignaturValidator {
    init {
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true")
        Init.init()
    }

    @Throws(SignatureException::class)
    fun validate(document: Document) {
        val signature = document.retrieveSignatureElement()
        val certificateFromSignature = signature.keyInfo.x509Certificate

        try {
            if (!signature.checkSignatureValue(certificateFromSignature) // Regel ID 50)
            ) {
                throw SignatureException("Invalid Signature!")
            }
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Invalid Signature!", e)
        }
    }
}
