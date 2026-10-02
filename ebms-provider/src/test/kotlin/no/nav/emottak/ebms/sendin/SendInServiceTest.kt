package no.nav.emottak.ebms.sendin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.SendInClient
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.utils.common.model.SendInRequest
import no.nav.emottak.utils.serialization.LENIENT_JSON_PARSER
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SendInServiceTest {

    private val capturedRequests = mutableListOf<HttpRequestData>()
    private val capturedBodies = mutableListOf<String>()

    private fun sendInService(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): SendInService {
        val engine = MockEngine { request ->
            capturedRequests.add(request)
            capturedBodies.add(String(request.body.toByteArray()))
            handler(request)
        }
        // Mirrors defaultHttpClient()/scopedAuthHttpClient() without CIO and auth
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { jsonLenient() }
        }
        return SendInService(SendInClient { client })
    }

    @Test
    fun `sendInAsynkron posts SendInRequest with correlation fields to fagmelding asynkron`() = runBlocking {
        val service = sendInService { respond("", HttpStatusCode.Accepted) }
        val payloadMessage = createPayloadMessage()

        service.sendInAsynkron(payloadMessage, partnerId = 42L)

        val request = capturedRequests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/fagmelding/asynkron", request.url.encodedPath)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())

        val sendInRequest = LENIENT_JSON_PARSER.decodeFromString<SendInRequest>(capturedBodies.single())
        assertEquals(payloadMessage.messageId, sendInRequest.messageId)
        assertEquals(payloadMessage.conversationId, sendInRequest.conversationId)
        assertEquals(payloadMessage.requestId, sendInRequest.requestId)
        assertEquals(payloadMessage.cpaId, sendInRequest.cpaId)
        assertEquals(payloadMessage.addressing, sendInRequest.addressing)
        assertEquals(payloadMessage.payload.contentId, sendInRequest.payloadId)
        assertContentEquals(payloadMessage.payload.bytes, sendInRequest.payload)
        assertEquals(payloadMessage.payload.signedBy, sendInRequest.signedOf)
        assertEquals(42L, sendInRequest.partnerId)
    }

    @Test
    fun `sendInAsynkron throws recoverable EbmsException when send-in responds with client error`() {
        val service = sendInService { respond("Ugyldig fagmelding", HttpStatusCode.BadRequest) }

        val exception = assertFailsWith<EbmsException> {
            runBlocking { service.sendInAsynkron(createPayloadMessage()) }
        }
        assertEquals("/fagmelding/asynkron", capturedRequests.single().url.encodedPath)
        assertTrue(exception.isRecoverable())
        assertEquals(ErrorCode.DELIVERY_FAILURE, exception.feil.single().code)
        assertTrue(exception.message!!.contains("400"))
        assertTrue(exception.message!!.contains("Ugyldig fagmelding"))
    }

    @Test
    fun `sendInAsynkron throws recoverable EbmsException when send-in responds with server error`() {
        val service = sendInService { respond("MQ utilgjengelig", HttpStatusCode.ServiceUnavailable) }

        val exception = assertFailsWith<EbmsException> {
            runBlocking { service.sendInAsynkron(createPayloadMessage()) }
        }
        assertTrue(exception.isRecoverable())
        assertEquals(ErrorCode.DELIVERY_FAILURE, exception.feil.single().code)
        assertTrue(exception.message!!.contains("503"))
        assertTrue(exception.message!!.contains("MQ utilgjengelig"))
    }

    private fun createPayloadMessage() = PayloadMessage(
        requestId = "request-id",
        messageId = "message-id",
        conversationId = "conversation-id",
        cpaId = "nav:qass:12345",
        addressing = Addressing(
            to = Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
            from = Party(listOf(PartyId("HER", "123456")), "Behandler"),
            service = "Sykmelding",
            action = "Registrering"
        ),
        payload = Payload(
            bytes = "<fagmelding/>".toByteArray(),
            contentType = "application/xml",
            contentId = "content-id",
            signedBy = "12345678901"
        ),
        duplicateElimination = true
    )
}
