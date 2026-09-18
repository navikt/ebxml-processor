package no.nav.emottak.payload

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.emottak.message.model.Payload
import no.nav.emottak.payload.helseid.NinResolver
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.util.marker
import no.nav.emottak.validering.sertifikat.CRLChecker
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessorTest : PayloadTestBase() {

    @AfterAll
    fun tearDown() = mockOAuth2Server.shutdown()

    private fun buildProcessor(ninResolver: NinResolver = mockk()): Processor {
        val crlChecker = mockk<CRLChecker>()
        every { crlChecker.getCRLRevocationInfo(any(), any()) } just runs
        return Processor(
            EventRegistrationServiceFake(),
            SertifikatValidator(crlChecker = crlChecker),
            ninResolver = ninResolver
        )
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

    @Test
    fun `validateReadablePayload leaves signedByOrg null when the signing certificate has no org number and leaves signedByPid null when ocspSjekk is disabled`() = runBlocking {
        setupEnv()
        val processor = buildProcessor()
        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload) // signering = true, ocspSjekk = false by default

        val result = processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)

        assertNull(result.signedByPid, "signedByPid must stay null when ocspSjekk is disabled")
        assertNull(result.signedByOrg, "signedByOrg must be null when no org number can be extracted from the signing certificate")
    }

    @Test
    fun `validateReadablePayload populates signedByPid via ninResolver when ocspSjekk is enabled and leaves signedByOrg null when signering is disabled`() = runBlocking {
        setupEnv()
        val expectedPid = "01010112345"
        val ninResolver = mockk<NinResolver>()
        coEvery { ninResolver.resolve(any<org.w3c.dom.Document>(), any()) } returns expectedPid
        val processor = buildProcessor(ninResolver)

        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload).let {
            it.copy(
                processing = it.processing.copy(
                    processConfig = it.processing.processConfig.copy(signering = false, ocspSjekk = true)
                )
            )
        }

        val result = processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)

        assertNull(result.signedByOrg, "signedByOrg must stay null when signering is disabled")
        assertEquals(expectedPid, result.signedByPid, "signedByPid must be populated from the ninResolver when ocspSjekk is enabled")
        assertEquals(expectedPid, result.signedBy, "legacy signedBy field must remain in sync with signedByPid")
    }

    @Test
    fun `validateReadablePayload leaves both signedByPid and signedByOrg null when neither signering nor ocspSjekk is enabled`() = runBlocking {
        setupEnv()
        val processor = buildProcessor()
        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload).let {
            it.copy(
                processing = it.processing.copy(
                    processConfig = it.processing.processConfig.copy(signering = false, ocspSjekk = false)
                )
            )
        }

        val result = processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)

        assertNull(result.signedByPid)
        assertNull(result.signedByOrg)
    }
}
