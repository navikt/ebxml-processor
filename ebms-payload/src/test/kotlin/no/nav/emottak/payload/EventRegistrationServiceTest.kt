@file:OptIn(ExperimentalUuidApi::class)

package no.nav.emottak.payload

import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.emottak.payload.error.SignatureException
import no.nav.emottak.payload.helseid.PidSource
import no.nav.emottak.payload.helseid.ResolvedPid
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.payload.util.EventRegistrationServiceImpl
import no.nav.emottak.util.mapCertificateDetails
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.uuid.ExperimentalUuidApi

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventRegistrationServiceTest : PayloadTestBase() {

    @AfterAll
    fun tearDown() = mockOAuth2Server.shutdown()

    private val eventLoggingService = mockk<EventLoggingService>(relaxed = true)
    private val eventRegistrationService = EventRegistrationServiceImpl(eventLoggingService)

    @BeforeEach
    fun resetMocks() = clearMocks(eventLoggingService)

    private fun loggedEvent(): Event {
        val event = slot<Event>()
        coVerify(exactly = 1) { eventLoggingService.logEvent(capture(event)) }
        return event.captured
    }

    private fun Event.eventDataMap(): Map<String, String> =
        Json.parseToJsonElement(eventData).jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test
    fun `registerSignatureValidationFailed adds certificate details and error message to event data`() = runBlocking {
        setupEnv()
        val request = baseRequest()
        val certificate = Fixtures.signingCertificate()

        eventRegistrationService.registerSignatureValidationFailed(request, certificate, SignatureException("Invalid Signature!"))

        with(loggedEvent()) {
            assertEquals(EventType.SIGNATURE_CHECK_FAILED, eventType)
            assertEquals(request.requestId, requestId.toString())
            assertEquals(request.messageId, messageId)
            assertEquals(request.conversationId, conversationId)
            assertEquals(request.payload.contentId, contentId)
            assertEquals(
                certificate.mapCertificateDetails() + (EventDataType.ERROR_MESSAGE.value to "Invalid Signature!"),
                eventDataMap()
            )
        }
    }

    @Test
    fun `registerSignatureValidationFailed without certificate only adds error message to event data`() = runBlocking {
        setupEnv()
        val request = baseRequest()

        eventRegistrationService.registerSignatureValidationFailed(request, null, SignatureException("No signature element found in payload"))

        assertEquals(
            mapOf(EventDataType.ERROR_MESSAGE.value to "No signature element found in payload"),
            loggedEvent().eventDataMap()
        )
    }

    @Test
    fun `registerSignatureValidationFailed uses exception class name when exception has no message`() = runBlocking {
        setupEnv()
        val request = baseRequest()

        eventRegistrationService.registerSignatureValidationFailed(request, null, NullPointerException())

        assertEquals(
            mapOf(EventDataType.ERROR_MESSAGE.value to "NullPointerException"),
            loggedEvent().eventDataMap()
        )
    }

    @Test
    fun `registerSignatureValidationSuccessful adds certificate details to event data`() = runBlocking {
        setupEnv()
        val request = baseRequest()
        val certificate = Fixtures.signingCertificate()

        eventRegistrationService.registerSignatureValidationSuccessful(request, certificate)

        with(loggedEvent()) {
            assertEquals(EventType.SIGNATURE_CHECK_SUCCESSFUL, eventType)
            assertEquals(certificate.mapCertificateDetails(), eventDataMap())
        }
    }

    @ParameterizedTest
    @EnumSource(PidSource::class)
    fun `registerPidRetrieved sends masked PID and source`(source: PidSource) = runBlocking {
        setupEnv()
        val request = baseRequest()
        val resolvedPid = ResolvedPid("01010112345", source)

        eventRegistrationService.registerPidRetrieved(request, resolvedPid)

        with(loggedEvent()) {
            assertEquals(EventType.OCSP_CHECK_SUCCESSFUL, eventType)
            assertEquals(request.requestId, requestId.toString())
            assertEquals(request.messageId, messageId)
            assertEquals(request.conversationId, conversationId)
            assertEquals(request.payload.contentId, contentId)
            assertEquals(mapOf("PID" to "010*******5", "source" to source.name), eventDataMap())
            assertFalse(eventData.contains(resolvedPid.pid))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1", "12", "123", "1234", "12345"])
    fun `registerPidRetrieved fully masks short PIDs`(pid: String) = runBlocking {
        setupEnv()

        eventRegistrationService.registerPidRetrieved(baseRequest(), ResolvedPid(pid, PidSource.OCSP))

        assertEquals(mapOf("PID" to "*".repeat(pid.length), "source" to "OCSP"), loggedEvent().eventDataMap())
    }

    @Test
    fun `registerPidRetrieved without PID preserves empty event data`() = runBlocking {
        setupEnv()

        eventRegistrationService.registerPidRetrieved(baseRequest(), null)

        assertEquals("{}", loggedEvent().eventData)
    }

    @ParameterizedTest
    @EnumSource(PidSource::class)
    fun `fake registerPidRetrieved uses the same masked event data`(source: PidSource) = runBlocking {
        setupEnv()
        val request = baseRequest()
        val resolvedPid = ResolvedPid("01010112345", source)
        val fake = spyk(EventRegistrationServiceFake())
        eventRegistrationService.registerPidRetrieved(request, resolvedPid)
        val eventData = loggedEvent().eventData

        fake.registerPidRetrieved(request, resolvedPid)

        coVerify(exactly = 1) {
            fake.registerEvent(EventType.OCSP_CHECK_SUCCESSFUL, request, eventData)
        }
    }
}
