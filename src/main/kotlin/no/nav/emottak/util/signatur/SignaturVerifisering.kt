package no.nav.emottak.util.signatur

import no.nav.emottak.util.createValidateContext
import no.nav.emottak.util.retrieveXMLSignature
import org.w3c.dom.Document
import javax.xml.crypto.dsig.XMLSignatureException


class SignaturVerifisering {

    fun validerSignatur(document: Document): Document {

        val validateContext = createValidateContext(document)
        val signature = retrieveXMLSignature(validateContext)

        try {
            return if (signature.validate(validateContext)) {
                validateAlgorithms(signature.signedInfo)
                document
            } else {
                val sv: Boolean = signature.signatureValue.validate(validateContext)
                var status = "Signature validation status: $sv"
                signature.signedInfo.references.forEach {
                    status += ", ${it.digestMethod.algorithm} validation status: ${it.validate(validateContext)}"
                }
                throw SignatureException("Sertifikat er ugyldig: $status")
            }
        } catch (signatureException: XMLSignatureException) {
            throw SignatureException("Feil ved validering av signatur", signatureException)
        } catch (algorithmNotSupported: AlgorithmNotSupportedException) {
            throw SignatureException("Feil ved validering av signatur", algorithmNotSupported)
        }
    }

}