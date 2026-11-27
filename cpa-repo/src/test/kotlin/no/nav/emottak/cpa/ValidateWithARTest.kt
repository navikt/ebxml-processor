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
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.emottak.cpa.feil.CpaValidationException
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
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidateWithARTest {

    @Test
    fun `outgoing request with missing CPA ID uses receiver HER ID to validate against latest valid CPA`() {
        val older = fallbackCpa("older")
        val newer = fallbackCpa("newer")
        val future = fallbackCpa("future").apply { start = Date.from(Instant.now().plus(1, ChronoUnit.DAYS)) }
        val expired = fallbackCpa("expired").apply { end = Date.from(Instant.now().minus(1, ChronoUnit.DAYS)) }
        checkFallback(
            outgoingRequest(),
            candidates = mapOf(
                Instant.parse("2025-01-01T00:00:00Z") to older,
                Instant.parse("2025-02-01T00:00:00Z") to newer,
                Instant.parse("2025-03-01T00:00:00Z") to future,
                Instant.parse("2025-04-01T00:00:00Z") to expired
            )
        ) { result, repository, partnerRepository ->
            assertTrue(result.valid(), "${result.error}")
            assertEquals("http://foo.bar", result.receiverHttpEndpoint)
            assertTrue(assertNotNull(result.cpaAddressing).to.partyId.any { it.type == "HER" && it.value == "8090595" })
            verify(exactly = 1) { repository.cpaByHerId("8090595") }
            verify(exactly = 1) { partnerRepository.findPartnerId("newer") }
        }
    }

    @Test
    fun `missing CPA without a valid fallback returns delivery failure`() {
        val expired = fallbackCpa("expired").apply { end = Date.from(Instant.now().minus(1, ChronoUnit.DAYS)) }
        for (candidates in listOf(emptyMap(), mapOf(Instant.now() to expired))) {
            checkFallback(outgoingRequest(), candidates = candidates) { result, _, _ ->
                assertEquals(ErrorCode.DELIVERY_FAILURE, assertNotNull(result.error).single().code)
            }
        }
    }

    @Test
    fun `fallback is not looked up for incoming requests or outgoing requests without receiver HER ID`() {
        val outgoing = outgoingRequest()
        val incoming = createValidValidationRequest().copy(cpaId = "missing")
        val noHer = outgoing.copy(addressing = outgoing.addressing.copy(to = Party(listOf(PartyId("ENH", "984886136")), "Utleverer")))
        for (request in listOf(incoming, noHer)) {
            checkFallback(request) { result, repository, _ ->
                assertEquals(ErrorCode.DELIVERY_FAILURE, assertNotNull(result.error).single().code)
                verify(exactly = 0) { repository.cpaByHerId(any()) }
            }
        }
    }

    @Test
    fun `existing CPA ID takes precedence over receiver HER ID`() {
        val cpa = fallbackCpa("existing")
        checkFallback(outgoingRequest().copy(cpaId = "existing"), cpaById = cpa) { result, repository, _ ->
            assertTrue(result.valid(), "${result.error}")
            verify(exactly = 0) { repository.cpaByHerId(any()) }
        }
    }

    @Test
    fun `fallback skips invalid certificates but propagates operational errors`() = runBlocking {
        val repository = mockk<CPARepository>()
        val validator = mockk<SertifikatValidator>()
        val older = fallbackCpa("older")
        val newer = fallbackCpa("newer")
        every { repository.cpaByHerId("8090595") } returns mapOf(
            Instant.parse("2025-01-01T00:00:00Z") to older,
            Instant.parse("2025-02-01T00:00:00Z") to newer
        )
        coEvery { validator.validateCertificate(any()) } throws CertificateValidationException("revoked") andThen Unit
        assertEquals(older, findLatestValidCpaByHerid("8090595", repository, validator))

        coEvery { validator.validateCertificate(any()) } throws CertificateValidationException("revoked")
        assertNull(findLatestValidCpaByHerid("8090595", repository, validator))

        coEvery { validator.validateCertificate(any()) } throws IllegalStateException("CRL unavailable")
        assertThrows<IllegalStateException> {
            runBlocking { findLatestValidCpaByHerid("8090595", repository, validator) }
        }
        Unit
    }

    private fun fallbackCpa(id: String): CollaborationProtocolAgreement =
        unmarshal(javaClass.getResource("/cpa/nav-qass-31162.xml")!!.readText(), CollaborationProtocolAgreement::class.java).apply {
            cpaid = id
            start = Date.from(Instant.now().minus(1, ChronoUnit.DAYS))
            end = Date.from(Instant.now().plus(1, ChronoUnit.DAYS))
        }

    private fun outgoingRequest() = ValidationRequest(
        direction = Direction.OUT,
        messageId = "message",
        conversationId = "conversation",
        cpaId = "missing",
        addressing = Addressing(
            Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
            Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
            "HarBorgerEgenandelFritak",
            "Svar"
        )
    )

    private fun checkFallback(
        request: ValidationRequest,
        candidates: Map<Instant, CollaborationProtocolAgreement> = emptyMap(),
        cpaById: CollaborationProtocolAgreement? = null,
        check: (ValidationResult, CPARepository, PartnerRepository) -> Unit
    ) = testApplication {
        val repository = mockk<CPARepository>()
        every { repository.findCpaWithPreferredSource(request.cpaId) } returns
            cpaById?.let { CPARepository.CpaAndPreferredSource(it, Instant.now(), PreferredSource.CPA) }
        every { repository.cpaByHerId(any()) } returns candidates
        every { repository.getProcessConfig(any(), any(), any()) } returns ProcessConfig(
            false, false, false, false, false, false, false, false, null, null
        )
        val validator = mockk<SertifikatValidator>(relaxed = true)
        val partnerRepository = mockk<PartnerRepository>(relaxed = true)
        val events = mockk<EventRegistrationService>(relaxed = true)
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { jsonLenient() }
            routing { validateCpa(repository, partnerRepository, events, validator, null) }
        }
        val client = createClient { install(ContentNegotiation) { jsonLenient() } }
        val response = client.post("/cpa/validate/test-request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        check(response.body(), repository, partnerRepository)
    }

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
        every { cpaRepository.cpaByHerId(any()) } returns emptyMap()
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
    fun `unknown service action is rejected before adresseregister lookup without retry`() {
        val engine = getFakeNhnAdresseregisterEngine()
        val adresseregisterValidator = AdresseregisterValidator(HttpClient(engine))
        val cpaRepository = mockk<CPARepository>()
        val sertifikatValidator = mockk<SertifikatValidator>()
        val request = createValidValidationRequest().let {
            it.copy(addressing = it.addressing.copy(action = "UnknownAction"))
        }

        val exception = assertThrows<CpaValidationException> {
            runBlocking {
                adresseregisterValidator.validateWithAR(cpaRepository, request, sertifikatValidator)
            }
        }

        assertFalse(exception.isRecoverable())
        assertTrue(engine.requestHistory.isEmpty())
        verify {
            cpaRepository wasNot Called
            sertifikatValidator wasNot Called
        }
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
            it.copy(addressing = it.addressing.copy(service = EBMS_SERVICE_URI, action = ACKNOWLEDGMENT_ACTION))
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
