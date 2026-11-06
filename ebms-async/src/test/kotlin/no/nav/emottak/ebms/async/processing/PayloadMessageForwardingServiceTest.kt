package no.nav.emottak.ebms.async.processing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.EbmsHttpDeliveryClient
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.sendin.SendInService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.PayloadMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import kotlin.test.assertFailsWith

class PayloadMessageForwardingServiceTest {

    private lateinit var sendInService: SendInService
    private lateinit var ebmsInPayloadProducer: EbmsMessageProducer
    private lateinit var cpaValidationService: CPAValidationService
    private lateinit var processingService: ProcessingService
    private lateinit var ebmsPayloadProducer: EbmsMessageProducer
    private lateinit var messagePendingAckRepository: MessagePendingAckRepository
    private lateinit var ebmsHttpDeliveryClient: EbmsHttpDeliveryClient
    private lateinit var forwardingService: PayloadMessageForwardingService

    @BeforeEach
    fun setUp() {
        org.apache.xml.security.Init.init()
        sendInService = mockk()
        ebmsInPayloadProducer = mockk()
        cpaValidationService = mockk()
        processingService = mockk()
        ebmsPayloadProducer = mockk()
        messagePendingAckRepository = mockk(relaxed = true)
        ebmsHttpDeliveryClient = mockk()
        forwardingService = PayloadMessageForwardingService(
            sendInService = sendInService,
            cpaValidationService = cpaValidationService,
            processingService = processingService,
            payloadRepository = mockk(relaxed = true),
            ebmsPayloadProducer = ebmsPayloadProducer,
            ebmsInPayloadProducer = ebmsInPayloadProducer,
            eventRegistrationService = EventRegistrationServiceFake(),
            messagePendingAckRepository = messagePendingAckRepository,
            ebmsHttpDeliveryClient = ebmsHttpDeliveryClient
        )
        mockkStatic(EbmsDocument::signer)
        every { any<EbmsDocument>().signer(any()) } returnsArgument (0)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(EbmsDocument::signer)
    }

    private fun setupOutgoingMocks(payloadMessage: PayloadMessage, receiverHttpEndpoint: String?) {
        coEvery { cpaValidationService.validateOutgoingMessage(any()) } returns validValidationResult().copy(
            receiverEmailAddress = listOf(EmailAddress("partner@example.com", EndpointTypeType.ALL_PURPOSE)),
            cpaAddressing = payloadMessage.addressing,
            receiverHttpEndpoint = receiverHttpEndpoint
        )
        coEvery { cpaValidationService.getMessageCharacteristicsType(any()) } returns createMessageCharacteristicsResponse()
        coEvery { processingService.proccessSyncOut(any(), any()) } answers { firstArg() }
    }

    @Test
    fun `returnMessageResponse delivers over HTTP and marks message acknowledged when partner replies with Acknowledgment`() = runBlocking {
        val payloadMessage = createPayloadMessage(givenService = "Trekkopplysning")
        setupOutgoingMocks(payloadMessage, "https://partner.example.com/ebms")
        coEvery { ebmsHttpDeliveryClient.deliver(any(), any()) } returns mockk()

        forwardingService.returnMessageResponse(payloadMessage)

        coVerify(exactly = 1) { ebmsHttpDeliveryClient.deliver("https://partner.example.com/ebms", any()) }
        coVerify(exactly = 0) { ebmsPayloadProducer.publishMessage(any(), any(), any()) }
        verify(exactly = 1) {
            messagePendingAckRepository.storeMessagePendingAck(any(), any(), any(), emptyList(), true, any())
        }
    }

    @Test
    fun `returnMessageResponse sends to payload topic when CPA has no HTTP endpoint`() = runBlocking {
        val payloadMessage = createPayloadMessage()
        setupOutgoingMocks(payloadMessage, null)
        coEvery { ebmsPayloadProducer.publishMessage(any(), any(), any()) } returns Result.success(mockk())

        forwardingService.returnMessageResponse(payloadMessage)

        coVerify(exactly = 1) { ebmsPayloadProducer.publishMessage(any(), any(), any()) }
        coVerify(exactly = 0) { ebmsHttpDeliveryClient.deliver(any(), any()) }
        verify(exactly = 1) {
            messagePendingAckRepository.storeMessagePendingAck(
                any(),
                any(),
                any(),
                listOf(EmailAddress("partner@example.com", EndpointTypeType.ALL_PURPOSE)),
                any(),
                any()
            )
        }
    }

    @Test
    fun `returnMessageResponse does not store pending ack when HTTP delivery fails or partner replies with MessageError`() {
        val payloadMessage = createPayloadMessage(givenService = "Trekkopplysning")
        setupOutgoingMocks(payloadMessage, "https://partner.example.com/ebms")
        coEvery { ebmsHttpDeliveryClient.deliver(any(), any()) } throws EbmsException("Sending feilet")

        assertFailsWith<EbmsException> {
            runBlocking { forwardingService.returnMessageResponse(payloadMessage) }
        }
        verify(exactly = 0) { messagePendingAckRepository.storeMessagePendingAck(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `forwardMessageWithAsyncResponse sends message to send-in over HTTP with partnerId`() = runBlocking {
        val payloadMessage = createPayloadMessage(givenService = MessageType.SYKMELDING.serviceName)
        coEvery { sendInService.sendInAsynkron(any(), any()) } just runs

        forwardingService.forwardMessageWithAsyncResponse(payloadMessage, 42L)

        coVerify(exactly = 1) { sendInService.sendInAsynkron(payloadMessage, 42L) }
        coVerify(exactly = 0) { sendInService.sendInSynkron(any(), any()) }
        coVerify(exactly = 0) { ebmsInPayloadProducer.publishMessage(any(), any(), any()) }
    }

    @Test
    fun `forwardMessageWithAsyncResponse propagates send-in failures`() {
        val payloadMessage = createPayloadMessage(givenService = MessageType.SYKMELDING.serviceName)
        coEvery { sendInService.sendInAsynkron(any(), any()) } throws Exception("send-in failed")

        assertFailsWith<Exception> {
            runBlocking { forwardingService.forwardMessageWithAsyncResponse(payloadMessage, 42L) }
        }
    }
}
