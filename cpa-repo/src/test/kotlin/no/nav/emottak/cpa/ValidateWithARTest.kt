package no.nav.emottak.cpa

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.PreferredSource
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.util.EventRegistrationService
import no.nav.emottak.cpa.validation.AdresseregisterValidator
import no.nav.emottak.message.ebxml.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.message.ebxml.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.message.ebxml.EbXMLConstants.MESSAGE_ERROR_ACTION
import no.nav.emottak.message.exception.CertificateValidationException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidateWithARTest {

    @Test
    fun `incoming signals prefer AR even when the CPA prefers CPA validation`() {
        for (action in listOf(ACKNOWLEDGMENT_ACTION, MESSAGE_ERROR_ACTION)) {
            validateSourceSelection(action, Direction.IN, cpaExists = true, expectAR = true)
        }
    }

    @Test
    fun `incoming signals can be validated without a CPA`() {
        for (action in listOf(ACKNOWLEDGMENT_ACTION, MESSAGE_ERROR_ACTION)) {
            validateSourceSelection(action, Direction.IN, cpaExists = false, expectAR = true)
        }
    }

    @Test
    fun `ordinary incoming messages and outgoing signals still use the configured source`() {
        validateSourceSelection("OppgjorsMelding", Direction.IN, cpaExists = false, expectAR = false)
        validateSourceSelection(ACKNOWLEDGMENT_ACTION, Direction.OUT, cpaExists = false, expectAR = false)
        validateSourceSelection(ACKNOWLEDGMENT_ACTION, Direction.IN, cpaExists = false, expectAR = false, service = "BehandlerKrav")
    }

    private fun validateSourceSelection(
        action: String,
        direction: Direction,
        cpaExists: Boolean,
        expectAR: Boolean,
        service: String = EBMS_SERVICE_URI
    ) = testApplication {
        val engine = getFakeNhnAdresseregisterEngine()
        val adresseregisterValidator = AdresseregisterValidator(
            HttpClient(engine) {
                install(ContentNegotiation) {
                    jsonLenient()
                }
            }
        )
        val cpaRepository = mockk<CPARepository>()
        every { cpaRepository.findCpaWithPreferredSource(any()) } returns if (cpaExists) {
            CPARepository.CpaAndPreferredSource(TestUtil.createValidTestCPA(), null, PreferredSource.CPA)
        } else {
            null
        }
        every { cpaRepository.getProcessConfig(any(), any(), any()) } returns ProcessConfig(
            false, false, false, false, false, false, false, false, null, null
        )
        val sertifikatValidator = mockk<SertifikatValidator>(relaxed = true)
        val partnerRepository = mockk<PartnerRepository>()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                jsonLenient()
            }
            routing {
                validateCpa(cpaRepository, partnerRepository, eventRegistrationService, sertifikatValidator, adresseregisterValidator)
            }
        }
        val httpClient = createClient {
            install(ContentNegotiation) {
                jsonLenient()
            }
        }
        val request = createValidValidationRequest().let {
            it.copy(direction = direction, addressing = it.addressing.copy(service = service, action = action))
        }

        val response = httpClient.post("/cpa/validate/test-request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expectAR, response.body<ValidationResult>().valid())
        assertEquals(expectAR, engine.requestHistory.any { it.url.encodedPath.endsWith("/8141253/signing") })
        coVerify(exactly = if (expectAR) 1 else 0) { sertifikatValidator.validateCertificate(any()) }
        coVerify(exactly = 0) { cpaRepository.updateCpaLastUsed(any()) }
    }

    @Test
    fun `Ugyldig signeringssertifikat fra adresseregisteret stopper validering`() {
        val engine = getFakeNhnAdresseregisterEngine()
        val adresseregisterValidator = AdresseregisterValidator(
            HttpClient(engine) {
                install(ContentNegotiation) {
                    jsonLenient()
                }
            }
        )
        val cpaRepository = mockk<CPARepository>()
        val sertifikatValidator = mockk<SertifikatValidator>()
        coEvery { sertifikatValidator.validateCertificate(any()) } throws CertificateValidationException("Sertifikat revokert")
        val validationRequest = createValidValidationRequest().let {
            it.copy(addressing = it.addressing.copy(service = EBMS_SERVICE_URI))
        }

        val exception = assertThrows<CertificateValidationException> {
            runBlocking {
                adresseregisterValidator.validateWithAR(cpaRepository, validationRequest, sertifikatValidator)
            }
        }

        assertEquals("Sertifikat revokert", exception.message)
        coVerify(exactly = 1) { sertifikatValidator.validateCertificate(any()) }
        val requestedPaths = engine.requestHistory.map { it.url.encodedPath }
        assertTrue(requestedPaths.any { it.endsWith("/8141253/signing") }, "$requestedPaths")
        assertTrue(requestedPaths.none { it.contains("communicationparty") || it.endsWith("/encryption") }, "$requestedPaths")
    }
}
