package no.nav.emottak.util.signatur

import org.junit.jupiter.api.Test

class SignaturVerifiseringTest {

    @Test
    fun `Payload signature er valid`() {
        val validator = SignaturVerifisering()
        val inputStream = SignaturVerifiseringTest::class.java.classLoader
            .getResourceAsStream("payload.xml")
        validator.validate(inputStream.readAllBytes())

    }

}