package no.nav.emottak.util.signatur

import no.nav.emottak.util.createDocument
import no.nav.emottak.util.retrieveSignatureElement
import org.apache.xml.security.signature.MissingResourceFailureException
import java.io.ByteArrayInputStream

class SignaturVerifisering {
    init {
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true")
        org.apache.xml.security.Init.init()
    }

    @Throws(SignatureException::class)
    fun validate(document: ByteArray) {
        // TODO Sjekk isNonRepudiation?
        val dom = createDocument(ByteArrayInputStream(document))
        val signature = dom.retrieveSignatureElement()
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
