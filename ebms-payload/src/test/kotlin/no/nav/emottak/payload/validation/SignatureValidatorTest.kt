package no.nav.emottak.payload.validation

import no.nav.emottak.payload.crypto.SignatureValidator
import no.nav.emottak.payload.error.SignatureException
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.retrieveSignatureElement
import org.apache.xml.security.utils.Constants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.w3c.dom.Document

class SignatureValidatorTest {

    private fun payloadDocument(): Document = createDocument(
        SignatureValidatorTest::class.java.classLoader.getResourceAsStream("payload.xml")!!
    )

    private fun Document.removeSignatureChild(localName: String) = apply {
        val element = getElementsByTagNameNS(Constants.SignatureSpecNS, localName).item(0)
        element.parentNode.removeChild(element)
    }

    @Test
    fun `Payload signature er valid`() {
        val validator = SignatureValidator()
        validator.validate(payloadDocument().retrieveSignatureElement())
    }

    @Test
    fun `Signatur uten KeyInfo gir SignatureException`() {
        val validator = SignatureValidator()
        val signature = payloadDocument().removeSignatureChild(Constants._TAG_KEYINFO).retrieveSignatureElement()
        assertThrows<SignatureException> { validator.validate(signature) }
    }

    @Test
    fun `Signatur med KeyInfo uten sertifikat gir SignatureException`() {
        val validator = SignatureValidator()
        val signature = payloadDocument().removeSignatureChild(Constants._TAG_X509DATA).retrieveSignatureElement()
        assertThrows<SignatureException> { validator.validate(signature) }
    }
}
