package no.nav.emottak.ebms.async.processing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.sendin.SendInService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class PayloadMessageForwardingServiceTest {

    private lateinit var sendInService: SendInService
    private lateinit var ebmsInPayloadProducer: EbmsMessageProducer
    private lateinit var forwardingService: PayloadMessageForwardingService

    @BeforeEach
    fun setUp() {
        sendInService = mockk()
        ebmsInPayloadProducer = mockk()
        forwardingService = PayloadMessageForwardingService(
            sendInService = sendInService,
            cpaValidationService = mockk(),
            processingService = mockk(),
            payloadRepository = mockk(),
            ebmsPayloadProducer = mockk(),
            ebmsInPayloadProducer = ebmsInPayloadProducer,
            eventRegistrationService = mockk(),
            messagePendingAckRepository = mockk()
        )
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
