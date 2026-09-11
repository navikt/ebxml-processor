package no.nav.emottak.util.signatur

import no.nav.emottak.util.createDocument
import no.nav.emottak.validering.signatur.SignaturValidator
import org.junit.jupiter.api.Test

class SignaturValidatorTest {

    @Test
    fun `Payload signature er valid`() {
        val validator = SignaturValidator()
        val inputStream = SignaturValidatorTest::class.java.classLoader
            .getResourceAsStream("payload.xml")!!
        validator.validate(createDocument(inputStream))
    }
}
