package no.nav.emottak.util.signatur

import java.io.ByteArrayInputStream
import java.util.Base64
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.retrieveSignatureElement
import org.apache.xml.security.signature.MissingResourceFailureException
import org.slf4j.LoggerFactory


class SignaturVerifisering {

    private val log = LoggerFactory.getLogger(SignaturVerifisering::class.java)

    init {
        org.apache.xml.security.Init.init()
    }

    @Throws(SignatureException::class)
    fun validate(document: ByteArray) {
        //TODO Sjekk isNonRepudiation?

        try {
            val base64 = Base64.getEncoder().encodeToString(document)
            log.debug("Document Base64: {}", base64)
        } catch (ignore: Exception) {
        }

        val dom = try {
            log.debug("Attempting to parse document into DOM")
            createDocument(ByteArrayInputStream(document))
        } catch (ex: Exception) {
            log.error("Error while parsing document into DOM", ex)
            throw SignatureException("Error while parsing document", ex)
        }

        val signature = dom.retrieveSignatureElement()
        val certificateFromSignature = signature.keyInfo.x509Certificate

        try {
            if (!signature.checkSignatureValue(certificateFromSignature) //Regel ID 50)
            ) throw SignatureException("Invalid Signature!")
        } catch (e: MissingResourceFailureException) {
            throw SignatureException("Invalid Signature!", e)
        }
    }

}