package no.nav.emottak.util.signatur

import no.nav.emottak.util.createDocument
import no.nav.emottak.util.retrieveSignatureElement
import org.apache.xml.security.signature.MissingResourceFailureException
import java.io.ByteArrayInputStream


class SignaturVerifisering {

    @Throws(SignatureException::class)
    fun validate(document: ByteArray) {
        //TODO Sjekk isNonRepudiation?
        val signature = retrieveSignatureElement(createDocument(ByteArrayInputStream(document)))
        val certificateFromSignature = signature.keyInfo.x509Certificate

        try {
            if (!signature.checkSignatureValue(certificateFromSignature) //Regel ID 50)
            ) throw SignatureException("Invalid Signature!")
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Invalid Signature!", e)
        }
    }

}