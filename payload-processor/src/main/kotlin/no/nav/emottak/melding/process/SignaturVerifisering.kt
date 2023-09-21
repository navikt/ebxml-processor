package no.nav.emottak.melding.process

import io.ktor.server.plugins.BadRequestException
import no.nav.emottak.melding.model.Melding
import no.nav.emottak.util.createValidateContext
import no.nav.emottak.util.retrieveXMLSignature
import no.nav.emottak.util.signatur.validateAlgorithms
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.signatur.AlgorithmNotSupportedException
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.crypto.dsig.XMLSignatureException

private val signatureVerifisering = SignaturVerifisering()

internal fun Melding.verifiserSignatur(): Melding {
    return this.copy(
        processedPayload = getByteArrayFromDocument(
            signatureVerifisering.validerSignatur(createDocument( ByteArrayInputStream(this.processedPayload)))
        ),
        signaturVerifisert = true
    )
}

class SignaturVerifisering {

    fun validerSignatur(document: Document): Document {

        val validateContext = createValidateContext(document)
        val signature = retrieveXMLSignature(validateContext)

        try {
            //TODO https://bugs.openjdk.java.net/browse/JDK-8259801
            //Må vurderes opp mot java 17 for sha1
            return if (signature.validate(validateContext)) {
                validateAlgorithms(signature.signedInfo)
                document
            } else {
                val sv: Boolean = signature.signatureValue.validate(validateContext)
                var status = "Signature validation status: $sv"
                signature.signedInfo.references.forEach {
                    status += ", ${it.digestMethod.algorithm} validation status: ${it.validate(validateContext)}"
                }
                throw BadRequestException("Sertifikat er ugyldig: $status")
            }
        } catch (signatureException: XMLSignatureException) {
            throw signatureException
        } catch (algorithmNotSupported: AlgorithmNotSupportedException) {
            throw BadRequestException(algorithmNotSupported.message,algorithmNotSupported)
        }
    }



}