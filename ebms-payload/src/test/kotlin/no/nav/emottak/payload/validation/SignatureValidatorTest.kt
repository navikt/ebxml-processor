package no.nav.emottak.payload.validation

import no.nav.emottak.payload.crypto.SignatureValidator
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.retrieveSignatureElement
import org.junit.jupiter.api.Test

class SignatureValidatorTest {

    @Test
    fun `Payload signature er valid`() {
        val validator = SignatureValidator()
        val inputStream = SignatureValidatorTest::class.java.classLoader
            .getResourceAsStream("payload.xml")!!
        validator.validate(createDocument(inputStream).retrieveSignatureElement())
    }
}
