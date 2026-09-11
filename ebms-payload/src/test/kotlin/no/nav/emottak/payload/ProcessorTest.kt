package no.nav.emottak.payload

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.emottak.message.model.Payload
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.util.marker
import no.nav.emottak.validering.sertifikat.CRLChecker
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessorTest : PayloadTestBase() {

    @AfterAll
    fun tearDown() = mockOAuth2Server.shutdown()

    private fun buildProcessor(): Processor {
        val crlChecker = mockk<CRLChecker>()
        every { crlChecker.getCRLRevocationInfo(any(), any()) } just runs
        return Processor(EventRegistrationServiceFake(), SertifikatValidator(crlChecker = crlChecker))
    }

    @Test
    fun `validateReadablePayload returns bytes from the decrypted-decompressed payload, not from the original request`() = runBlocking {
        setupEnv()
        val processor = buildProcessor()
        val readablePayload: Payload = Fixtures.validEgenandelForesporsel()

        // Simulates the still encrypted/compressed bytes originally received on the request,
        // which must not leak into the response once decryption/decompression has taken place.
        val originalRequestPayload = readablePayload.copy(bytes = "not-the-real-content".toByteArray())
        val request = baseRequest(payload = originalRequestPayload)

        val result = processor.validateReadablePayload(
            request.marker(),
            readablePayload,
            request,
            request.processing.processConfig
        )

        assertTrue(result.bytes.contentEquals(readablePayload.bytes), "Expected returned payload bytes to match the readable (decrypted/decompressed) payload")
        assertFalse(result.bytes.contentEquals(originalRequestPayload.bytes), "Returned payload bytes must not be the original, unprocessed request payload bytes")
    }
}
