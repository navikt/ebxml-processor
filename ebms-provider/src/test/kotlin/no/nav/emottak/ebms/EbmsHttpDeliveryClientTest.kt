package no.nav.emottak.ebms

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.xml.asString
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val ENDPOINT = "https://partner.example.com/ebms"

class EbmsHttpDeliveryClientTest {

    private val capturedRequests = mutableListOf<HttpRequestData>()

    init {
        org.apache.xml.security.Init.init()
    }

    private val sentMessage = payloadMessage()

    private fun client(
        status: HttpStatusCode,
        body: String = "",
        headers: Headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
    ): EbmsHttpDeliveryClient {
        val engine = MockEngine { request ->
            capturedRequests.add(request)
            respond(body, status, headers)
        }
        return EbmsHttpDeliveryClient({ HttpClient(engine) { expectSuccess = false } })
    }

    private fun acknowledgmentXml() = sentMessage.createAcknowledgment().toEbmsDokument().document.asString()

    private fun messageErrorXml() = sentMessage.createMessageError(
        listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signatur ugyldig"))
    ).toEbmsDokument().document.asString()

    private fun assertRecoverableDeliveryFailure(client: EbmsHttpDeliveryClient): EbmsException {
        val exception = assertFailsWith<EbmsException> {
            runBlocking { client.deliver(ENDPOINT, sentMessage.toEbmsDokument()) }
        }
        assertTrue(exception.isRecoverable())
        assertEquals(ErrorCode.DELIVERY_FAILURE, exception.feil.single().code)
        return exception
    }

    @Test
    fun `deliver posts payload as multipart related and returns Acknowledgment from sync reply`() = runBlocking {
        val acknowledgment = client(HttpStatusCode.OK, acknowledgmentXml()).deliver(ENDPOINT, sentMessage.toEbmsDokument())

        val request = capturedRequests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ENDPOINT, request.url.toString())
        assertEquals("ebXML", request.headers["SOAPAction"])
        assertTrue(request.body.contentType.toString().startsWith("multipart/related"))
        assertEquals(sentMessage.messageId, acknowledgment.refToMessageId)
    }

    @Test
    fun `deliver reads Acknowledgment from multipart related sync reply`() = runBlocking {
        val boundary = "reply-boundary"
        val body = "--$boundary\r\n" +
            "Content-Type: text/xml\r\n" +
            "Content-ID: <ack-envelope>\r\n\r\n" +
            acknowledgmentXml() + "\r\n" +
            "--$boundary--\r\n"
        val headers = headersOf(
            HttpHeaders.ContentType,
            """multipart/related;boundary="$boundary";start="<ack-envelope>";type="text/xml""""
        )

        val acknowledgment = client(HttpStatusCode.OK, body, headers).deliver(ENDPOINT, sentMessage.toEbmsDokument())

        assertEquals(sentMessage.messageId, acknowledgment.refToMessageId)
    }

    @Test
    fun `deliver reads base64 encoded Acknowledgment from multipart related sync reply`() = runBlocking {
        val boundary = "reply-boundary"
        val body = "--$boundary\r\n" +
            "Content-Type: text/xml\r\n" +
            "Content-Transfer-Encoding: base64\r\n" +
            "Content-ID: <ack-envelope>\r\n\r\n" +
            java.util.Base64.getMimeEncoder().encodeToString(acknowledgmentXml().toByteArray()) + "\r\n" +
            "--$boundary--\r\n"
        val headers = headersOf(HttpHeaders.ContentType, """multipart/related;boundary="$boundary";type="text/xml"""")

        val acknowledgment = client(HttpStatusCode.OK, body, headers).deliver(ENDPOINT, sentMessage.toEbmsDokument())

        assertEquals(sentMessage.messageId, acknowledgment.refToMessageId)
    }

    @Test
    fun `deliver throws recoverable delivery failure when sync reply is MessageError`() {
        val exception = assertRecoverableDeliveryFailure(client(HttpStatusCode.OK, messageErrorXml()))
        assertTrue(exception.message!!.contains("Signatur ugyldig"))
    }

    @Test
    fun `deliver throws recoverable delivery failure when 2xx reply has no ebXML message`() {
        assertRecoverableDeliveryFailure(client(HttpStatusCode.OK, "", headersOf()))
        assertRecoverableDeliveryFailure(client(HttpStatusCode.OK, "<ikke-ebxml/>"))
    }

    @Test
    fun `deliver throws recoverable delivery failure on non-2xx response`() {
        val exception = assertRecoverableDeliveryFailure(client(HttpStatusCode.ServiceUnavailable, "nede"))
        assertTrue(exception.message!!.contains("503"))
    }

    private fun payloadMessage() = PayloadMessage(
        requestId = "request-id",
        messageId = "message-id",
        conversationId = "conversation-id",
        cpaId = "nav:qass:12345",
        addressing = Addressing(
            to = Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
            from = Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
            service = "Trekkopplysning",
            action = "Svar"
        ),
        payload = Payload(
            bytes = "<fagmelding/>".toByteArray(),
            contentType = "application/xml",
            contentId = "content-id"
        ),
        duplicateElimination = false
    )
}
