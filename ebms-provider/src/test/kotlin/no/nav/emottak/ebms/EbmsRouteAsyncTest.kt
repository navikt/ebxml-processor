package no.nav.emottak.ebms

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.ebxml.acknowledgment
import no.nav.emottak.message.ebxml.errorList
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.DocumentType
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.message.xml.xmlMarshaller
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.xmlsoap.schemas.soap.envelope.Envelope
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EbmsRouteAsyncTest {
    private val cpaValidationService = mockk<CPAValidationService>()
    private val processingService = mockk<ProcessingService>()
    private val sendInService = mockk<SendInService>()
    private val certificate = mockk<SignatureDetails>()
    private val payloadProcessing = mockk<PayloadProcessing>()

    @BeforeEach
    fun setup() {
        org.apache.xml.security.Init.init()
        mockkStatic(EbmsDocument::signer)
        every { any<EbmsDocument>().signer(any()) } returnsArgument 0
        every { payloadProcessing.signingCertificate } returns certificate
        coEvery { cpaValidationService.validateIncomingMessage(any(), any()) } returns ValidationResult(
            payloadProcessing = payloadProcessing,
            partnerId = 123L
        )
        coEvery { processingService.processSyncIn(any(), payloadProcessing) } answers {
            firstArg<PayloadMessage>().copy(payload = Payload("processed".toByteArray(), "text/xml")) to Direction.IN
        }
        coEvery { sendInService.sendInAsynkron(any(), any()) } returns Unit
        coEvery { cpaValidationService.validateOutgoingMessage(any()) } returns ValidationResult(
            payloadProcessing = payloadProcessing
        )
    }

    @AfterEach
    fun cleanup() {
        unmockkStatic(EbmsDocument::signer)
    }

    @Test
    fun `Trekkopplysning returns signed acknowledgment after successful forwarding`() = assertSuccessfulForwarding("Trekkopplysning")

    @Test
    fun `other services return signed acknowledgment after successful forwarding`() = assertSuccessfulForwarding("HarBorgerEgenandelFritak")

    private fun assertSuccessfulForwarding(service: String) = testAsync(service) { status, envelope ->
        assertEquals(HttpStatusCode.Accepted, status)
        val header = envelope.header!!
        val acknowledgment = assertNotNull(header.acknowledgment())
        assertEquals("e17eb03e-9e43-43fb-874c-1fde9a28c308", acknowledgment.refToMessageId)
        assertEquals("nav:qass:31162", header.messageHeader().cpaId)
        assertEquals("e17eb03e-9e43-43fb-874c-1fde9a28c308", header.messageHeader().conversationId)
        assertNull(header.errorList())
        coVerifyOrder {
            cpaValidationService.validateIncomingMessage(any(), true)
            processingService.processSyncIn(any(), payloadProcessing)
            sendInService.sendInAsynkron(
                match { it.addressing.service == service && it.payload.bytes.contentEquals("processed".toByteArray()) },
                123L
            )
            cpaValidationService.validateOutgoingMessage(ofType<Acknowledgment>())
        }
        verify(exactly = 1) { any<EbmsDocument>().signer(certificate) }
        coVerify(exactly = 0) {
            sendInService.sendInSynkron(any(), any())
            processingService.proccessSyncOut(any(), any())
        }
    }

    @Test
    fun `forwarding failure returns MessageError instead of accepted`() {
        coEvery { sendInService.sendInAsynkron(any(), any()) } throws EbmsException(
            "Delivery failed",
            errorCode = ErrorCode.DELIVERY_FAILURE
        )
        testAsync { status, envelope ->
            assertEquals(HttpStatusCode.OK, status)
            assertNull(envelope.header!!.acknowledgment())
            assertEquals(ErrorCode.DELIVERY_FAILURE.value, envelope.header!!.errorList()!!.error.first().errorCode)
            coVerify(exactly = 0) { cpaValidationService.validateOutgoingMessage(any()) }
        }
    }

    @Test
    fun `incoming processing failure does not forward or acknowledge`() {
        coEvery { processingService.processSyncIn(any(), any()) } throws EbmsException("Processing failed")
        testAsync { status, envelope ->
            assertEquals(HttpStatusCode.OK, status)
            assertNotNull(envelope.header!!.errorList())
            assertNull(envelope.header!!.acknowledgment())
            coVerify(exactly = 0) { sendInService.sendInAsynkron(any(), any()) }
        }
    }

    @Test
    fun `outgoing acknowledgment validation failure does not return accepted`() {
        coEvery { cpaValidationService.validateOutgoingMessage(any()) } throws EbmsException("Invalid acknowledgment")
        testAsync { status, envelope ->
            assertEquals(HttpStatusCode.OK, status)
            assertNotNull(envelope.header!!.errorList())
            assertNull(envelope.header!!.acknowledgment())
        }
    }

    @Test
    fun `incoming validation failure does not process forward or acknowledge`() {
        coEvery { cpaValidationService.validateIncomingMessage(any(), any()) } throws EbmsException("Invalid incoming message")
        testAsync { status, envelope ->
            assertEquals(HttpStatusCode.OK, status)
            assertNotNull(envelope.header!!.errorList())
            assertNull(envelope.header!!.acknowledgment())
            coVerify(exactly = 0) {
                processingService.processSyncIn(any(), any())
                sendInService.sendInAsynkron(any(), any())
                cpaValidationService.validateOutgoingMessage(any())
            }
        }
    }

    @Test
    fun `outgoing processing result is not forwarded as an incoming message or acknowledged`() {
        coEvery { processingService.processSyncIn(any(), any()) } answers {
            firstArg<PayloadMessage>() to Direction.OUT
        }
        testAsync { status, envelope ->
            assertEquals(HttpStatusCode.OK, status)
            assertNotNull(envelope.header!!.errorList())
            assertNull(envelope.header!!.acknowledgment())
            coVerify(exactly = 0) {
                sendInService.sendInAsynkron(any(), any())
                cpaValidationService.validateOutgoingMessage(any())
            }
        }
    }

    @Test
    fun `unexpected forwarding failure returns SOAP fault instead of accepted`() {
        coEvery { sendInService.sendInAsynkron(any(), any()) } throws IllegalStateException("Unexpected forwarding failure")
        testAsync { status, envelope ->
            assertEquals(HttpStatusCode.InternalServerError, status)
            assertNull(envelope.header?.acknowledgment())
            assertNotNull(envelope.body)
            coVerify(exactly = 0) { cpaValidationService.validateOutgoingMessage(any()) }
        }
    }

    @Test
    fun `Trekkopplysning on sync endpoint returns synchronous payload response`() = testApplication {
        coEvery { sendInService.sendInSynkron(any(), any()) } answers {
            val message = firstArg<PayloadMessage>()
            mockk {
                every { requestId } returns message.requestId
                every { messageId } returns "response-message-id"
                every { conversationId } returns message.conversationId
                every { addressing } returns message.addressing.copy(
                    from = message.addressing.to,
                    to = message.addressing.from,
                    action = "Svar"
                )
                every { payload } returns "<response/>".toByteArray()
            }
        }
        coEvery { processingService.proccessSyncOut(any(), any()) } answers { firstArg<PayloadMessage>() }
        application {
            ebmsProviderModule(cpaValidationService, processingService, sendInService)
        }
        val response = client.post("/ebms/sync", request("Trekkopplysning").asHttpRequest())
        assertEquals(HttpStatusCode.OK, response.status)
        val reply = assertNotNull(response.receiveEbmsSyncReply())
        assertEquals(DocumentType.PAYLOAD, reply.documentType())
        assertEquals("Svar", reply.messageHeader().action)
        assertEquals("e17eb03e-9e43-43fb-874c-1fde9a28c308", reply.messageHeader().messageData.refToMessageId)
        coVerify(exactly = 1) {
            sendInService.sendInSynkron(any(), 123L)
            processingService.proccessSyncOut(any(), payloadProcessing)
        }
        coVerify(exactly = 0) { sendInService.sendInAsynkron(any(), any()) }
    }

    private fun testAsync(
        service: String = "Trekkopplysning",
        assertResponse: suspend (HttpStatusCode, Envelope) -> Unit
    ) = testApplication {
        application {
            ebmsProviderModule(cpaValidationService, processingService, sendInService)
        }
        val response = client.post("/ebms/async", request(service).asHttpRequest())
        assertResponse(response.status, xmlMarshaller.unmarshal(response.bodyAsText(), Envelope::class.java))
    }

    private fun request(service: String): MultipartRequest {
        val multipart = TestData.HarBorgerEgenandel.harBorgerEgenandelFritakRequestWithFormItem
        val soapPart = multipart.parts.first()
        return multipart.modify(
            soapPart to soapPart.payload(soapPart.payload.replace("HarBorgerEgenandelFritak", service))
        )
    }
}
