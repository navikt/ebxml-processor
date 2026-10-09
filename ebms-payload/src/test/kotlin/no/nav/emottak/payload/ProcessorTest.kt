package no.nav.emottak.payload

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.emottak.message.exception.CertificateValidationException
import no.nav.emottak.message.exception.SignatureValidationException
import no.nav.emottak.message.model.Payload
import no.nav.emottak.payload.crypto.SignatureValidator
import no.nav.emottak.payload.error.CertificateException
import no.nav.emottak.payload.error.SignatureException
import no.nav.emottak.payload.helseid.NinResolver
import no.nav.emottak.payload.helseid.PidSource
import no.nav.emottak.payload.helseid.ResolvedPid
import no.nav.emottak.payload.util.EventRegistrationService
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.marker
import no.nav.emottak.validering.sertifikat.CRLChecker
import no.nav.emottak.validering.sertifikat.CRLException
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.apache.xml.security.utils.Constants
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessorTest : PayloadTestBase() {

    @AfterAll
    fun tearDown() = mockOAuth2Server.shutdown()

    private fun buildProcessor(ninResolver: NinResolver = mockk()): Processor {
        val crlChecker = mockk<CRLChecker>()
        coEvery { crlChecker.getCRLRevocationInfo(any(), any()) } just runs
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
        coEvery { ninResolver.resolve(any<org.w3c.dom.Document>(), any()) } returns ResolvedPid(expectedPid, PidSource.OCSP)
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

    @Test
    fun `validateReadablePayload propagates technical CRL fetch failures instead of converting them into a PayloadException`() = runBlocking {
        setupEnv()
        val crlChecker = mockk<CRLChecker>()
        coEvery { crlChecker.getCRLRevocationInfo(any(), any()) } throws CRLException("CRL endpoint unreachable")
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val processor = Processor(
            eventRegistrationService,
            SertifikatValidator(crlChecker = crlChecker)
        )
        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload) // signering = true by default

        // A technical/transient CRL retrieval failure must propagate as-is (not be wrapped in a
        // PayloadException/AppRec-producing exception), so ebms-async's retry service can retry it.
        val thrown = assertThrows<CRLException> {
            processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)
        }
        assertEquals("CRL endpoint unreachable", thrown.message)
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationFailed(any(), any(), any()) }
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationSuccessful(any(), any()) }
    }

    @Test
    fun `validateReadablePayload registers SIGNATURE_CHECK_FAILED with certificate when the XML signature is invalid`() = runBlocking {
        setupEnv()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val signatureValidator = mockk<SignatureValidator>()
        every { signatureValidator.validate(any()) } throws SignatureException("Invalid Signature!")
        val processor = Processor(
            eventRegistrationService,
            mockk<SertifikatValidator>(),
            signaturValidator = signatureValidator
        )
        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload)

        assertThrows<SignatureException> {
            processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)
        }

        coVerify(exactly = 1) { eventRegistrationService.registerSignatureValidationFailed(request, isNull(inverse = true), ofType<SignatureException>()) }
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationSuccessful(any(), any()) }
    }

    @Test
    fun `validateReadablePayload registers SIGNATURE_CHECK_FAILED without certificate when the signature element is missing`() = runBlocking {
        setupEnv()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val processor = Processor(eventRegistrationService, mockk<SertifikatValidator>())
        val payload: Payload = Fixtures.validEgenandelForesporsel().copy(bytes = "<unsigned/>".toByteArray())
        val request = baseRequest(payload = payload)

        val thrown = assertThrows<SignatureException> {
            processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)
        }

        assertTrue(thrown.cause is SignatureValidationException)
        coVerify(exactly = 1) { eventRegistrationService.registerSignatureValidationFailed(request, null, ofType<SignatureValidationException>()) }
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationSuccessful(any(), any()) }
    }

    @Test
    fun `validateReadablePayload registers SIGNATURE_CHECK_FAILED and throws CertificateException with serial number when certificate validation fails`() = runBlocking {
        setupEnv()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val sertifikatValidator = mockk<SertifikatValidator>()
        coEvery { sertifikatValidator.validateCertificate(any()) } throws CertificateValidationException("Sertifikat er utløpt")
        val processor = Processor(eventRegistrationService, sertifikatValidator)
        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload)
        val certificate = Fixtures.signingCertificate()

        val thrown = assertThrows<CertificateException> {
            processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)
        }

        assertEquals("Sertifikat er utløpt, serial number: ${certificate.serialNumber.toString(16)}", thrown.message)
        coVerify(exactly = 1) { eventRegistrationService.registerSignatureValidationFailed(request, certificate, ofType<CertificateValidationException>()) }
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationSuccessful(any(), any()) }
    }

    @Test
    fun `validateReadablePayload registers SIGNATURE_CHECK_FAILED without certificate when the signature has no readable certificate`() = runBlocking {
        setupEnv()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val signatureValidator = mockk<SignatureValidator>()
        val sertifikatValidator = mockk<SertifikatValidator>()
        val processor = Processor(eventRegistrationService, sertifikatValidator, signaturValidator = signatureValidator)
        val payload: Payload = Fixtures.validEgenandelForesporsel().let { signed ->
            val document = createDocument(signed.bytes.inputStream())
            val keyInfo = document.getElementsByTagNameNS(Constants.SignatureSpecNS, Constants._TAG_KEYINFO).item(0)
            keyInfo.parentNode.removeChild(keyInfo)
            signed.copy(bytes = getByteArrayFromDocument(document))
        }
        val request = baseRequest(payload = payload)

        assertThrows<CertificateException> {
            processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)
        }

        coVerify(exactly = 1) { eventRegistrationService.registerSignatureValidationFailed(request, null, any()) }
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationSuccessful(any(), any()) }
        verify(exactly = 0) { signatureValidator.validate(any()) }
        coVerify(exactly = 0) { sertifikatValidator.validateCertificate(any()) }
    }

    @Test
    fun `validateReadablePayload registers SIGNATURE_CHECK_SUCCESSFUL and OCSP_CHECK_SUCCESSFUL when validation succeeds`() = runBlocking {
        setupEnv()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val signatureValidator = mockk<SignatureValidator>()
        every { signatureValidator.validate(any()) } just runs
        val sertifikatValidator = mockk<SertifikatValidator>()
        coEvery { sertifikatValidator.validateCertificate(any()) } just runs
        val ninResolver = mockk<NinResolver>()
        val resolvedPid = ResolvedPid("01010112345", PidSource.OCSP)
        coEvery { ninResolver.resolve(any<org.w3c.dom.Document>(), any()) } returns resolvedPid
        val processor = Processor(
            eventRegistrationService,
            sertifikatValidator,
            signaturValidator = signatureValidator,
            ninResolver = ninResolver
        )
        val payload: Payload = Fixtures.validEgenandelForesporsel()
        val request = baseRequest(payload = payload).withOCSP()

        processor.validateReadablePayload(request.marker(), payload, request, request.processing.processConfig)

        coVerify(exactly = 1) { eventRegistrationService.registerSignatureValidationSuccessful(request, Fixtures.signingCertificate()) }
        coVerify(exactly = 1) { eventRegistrationService.registerPidRetrieved(request, resolvedPid) }
        coVerify(exactly = 0) { eventRegistrationService.registerSignatureValidationFailed(any(), any(), any()) }
    }
}
