package no.nav.emottak.cpa

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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.util.EventRegistrationService
import no.nav.emottak.cpa.validation.AdresseregisterValidator
import no.nav.emottak.message.ebxml.EbXMLConstants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.message.ebxml.EbXMLConstants.EBMS_SERVICE_URI
import no.nav.emottak.message.ebxml.EbXMLConstants.MESSAGE_ERROR_ACTION
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.validering.sertifikat.SertifikatValidator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServiceActionValidationTest {
    @Test
    fun `unknown service is rejected before lookup without retry in both directions`() {
        Direction.entries.forEach { direction ->
            checkValidation(request(direction, "UnknownService", "OppgjorsMelding"), allowed = false)
        }
    }

    @Test
    fun `unknown action is rejected before lookup without retry in both directions`() {
        Direction.entries.forEach { direction ->
            checkValidation(request(direction, "BehandlerKrav", "UnknownAction"), allowed = false)
        }
    }

    @Test
    fun `service and action must be allowed together and for the requested direction`() {
        checkValidation(request(Direction.IN, "Sykmelding", "OppgjorsMelding"), allowed = false)
        checkValidation(request(Direction.OUT, "BehandlerKrav", "OppgjorsMelding"), allowed = false)
        checkValidation(request(Direction.IN, "BehandlerKrav", "Svarmelding"), allowed = false)
    }

    @Test
    fun `allowed combinations proceed to CPA lookup regardless of request roles`() {
        checkValidation(request(Direction.IN, "BehandlerKrav", "OppgjorsMelding"), allowed = true)
        checkValidation(request(Direction.OUT, "BehandlerKrav", "Svarmelding"), allowed = true)
    }

    @Test
    fun `valid ebms signals proceed to lookup in both directions`() {
        Direction.entries.forEach { direction ->
            listOf(ACKNOWLEDGMENT_ACTION, MESSAGE_ERROR_ACTION).forEach { action ->
                checkValidation(request(direction, EBMS_SERVICE_URI, action), allowed = true)
            }
        }
    }

    @Test
    fun `unknown ebms signal action is rejected without retry`() {
        checkValidation(request(Direction.IN, EBMS_SERVICE_URI, "UnknownAction"), allowed = false)
    }

    private fun request(direction: Direction, service: String, action: String): ValidationRequest {
        val request = createValidValidationRequest()
        return request.copy(
            direction = direction,
            addressing = request.addressing.copy(
                service = service,
                action = action,
                from = request.addressing.from.copy(role = "UnknownFromRole"),
                to = request.addressing.to.copy(role = "UnknownToRole")
            )
        )
    }

    private fun checkValidation(request: ValidationRequest, allowed: Boolean) = testApplication {
        val cpaRepository = mockk<CPARepository>()
        val partnerRepository = mockk<PartnerRepository>()
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val sertifikatValidator = mockk<SertifikatValidator>()
        val adresseregisterValidator = mockk<AdresseregisterValidator>()
        if (allowed) {
            every { cpaRepository.findCpaWithPreferredSource(request.cpaId) } returns null
        }
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                jsonLenient()
            }
            routing {
                validateCpa(cpaRepository, partnerRepository, eventRegistrationService, sertifikatValidator, adresseregisterValidator)
            }
        }
        val client = createClient {
            install(ContentNegotiation) {
                jsonLenient()
            }
        }
        val response = client.post("/cpa/validate/requestId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val error = assertNotNull(response.body<ValidationResult>().error).single()
        assertEquals(ErrorCode.DELIVERY_FAILURE, error.code)
        assertEquals(allowed, error.recoverable)
        if (allowed) {
            verify(exactly = 1) { cpaRepository.findCpaWithPreferredSource(request.cpaId) }
        } else {
            verify { cpaRepository wasNot Called }
        }
        verify {
            partnerRepository wasNot Called
            sertifikatValidator wasNot Called
            adresseregisterValidator wasNot Called
        }
        coVerify(exactly = 1) {
            eventRegistrationService.registerEvent(EventType.VALIDATION_AGAINST_CPA_FAILED, request, "requestId", any())
        }
    }
}
