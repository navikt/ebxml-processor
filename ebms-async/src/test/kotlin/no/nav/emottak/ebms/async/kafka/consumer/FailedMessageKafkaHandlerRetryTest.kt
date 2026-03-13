package no.nav.emottak.ebms.async.kafka.consumer

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class FailedMessageKafkaHandlerRetryTest {

    private lateinit var cpaValidationService: CPAValidationService
    private lateinit var eventRegistrationService: EventRegistrationService
    private lateinit var signalSender: suspend (EbmsDocument, List<EmailAddress>) -> Unit
    private lateinit var handler: FailedMessageKafkaHandler

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        cpaValidationService = mockk()
        eventRegistrationService = mockk<EventRegistrationService>()
        signalSender = mockk()
        coEvery { signalSender(any(), any()) } just Runs

        mockkStatic(EbmsDocument::signer)
        every {
            any<EbmsDocument>().signer(any())
        } returnsArgument(0)

        initHandler()
    }

    private fun initHandler() {
        System.setProperty("EBMS_PAYLOAD_PRODUCER", "true")
        System.setProperty("EBMS_SIGNAL_PRODUCER", "true")
        System.setProperty("EBMS_RETRY_QUEUE", "true")
        handler = mockk<FailedMessageKafkaHandler>(relaxed = true)
        every { handler.cpaValidationService } returns cpaValidationService
        every { handler.eventRegistrationService } returns eventRegistrationService
        every { handler.signalSender } returns signalSender
        coEvery {
            handler.sendToRetryQueueIncoming(any(), any(), any())
        } just runs
        coEvery {
            handler.sendToRetryIfShouldBeRetried(any(), any(), any(), any(), any())
        } answers { callOriginal() }
        coEvery {
            handler.incomingRetryEval(any(), any(), any(), any())
        } answers { callOriginal() }
        coEvery {
            handler.outgoingRetryEval(any(), any(), any(), any())
        } answers { callOriginal() }
        every {
            handler.decideRetry(any(), any(), any())
        } answers { callOriginal() }
        every {
            handler.isExpired(any())
        } answers { callOriginal() }
    }

    @Test
    fun `isExpired returns true for past ttl`() {
        val past = Instant.now().minusSeconds(60)
        assertTrue(handler.isExpired(past))
    }

    @Test
    fun `isExpired returns false for future ttl`() {
        val future = Instant.now().plusSeconds(60)
        assertFalse(handler.isExpired(future))
    }

    @Test
    fun `isExpired returns true for now instant as well`() {
        val now = Instant.now()
        assertTrue(handler.isExpired(now))
    }

    @Test
    fun `decideRetry returns TTL_EXPIRED when ttl is in past`() {
        val past = Instant.now().minusSeconds(10)
        val (decision, reason) = handler.decideRetry(ttl = past, retriedAlready = 0, maxRetries = 5)
        assertEquals(FailedMessageKafkaHandler.RetryDecision.TTL_EXPIRED, decision)
        assertTrue(reason.contains("TimeToLive expired"))
    }

    @Test
    fun `decideRetry returns MAX_RETRIES_EXCEEDED when retriedAlready is at least maxRetries`() {
        val (decision, reason) = handler.decideRetry(ttl = null, retriedAlready = 3, maxRetries = 3)
        assertEquals(FailedMessageKafkaHandler.RetryDecision.MAX_RETRIES_EXCEEDED, decision)
        assertTrue(reason.contains("Retried too many times"))
    }

    @Test
    fun `decideRetry returns RETRY when ttl is present and not expired`() {
        val future = Instant.now().plusSeconds(3600)
        val (decision, reason) = handler.decideRetry(ttl = future, retriedAlready = 1, maxRetries = 5)
        assertEquals(FailedMessageKafkaHandler.RetryDecision.RETRY, decision)
        assertTrue(reason.contains("Within ebXML TimeToLive"))
    }

    @Test
    fun `decideRetry returns RETRY when ttl is null and retried less than max`() {
        val (decision, reason) = handler.decideRetry(ttl = null, retriedAlready = 0, maxRetries = 5)
        assertEquals(FailedMessageKafkaHandler.RetryDecision.RETRY, decision)
        assertTrue(reason.contains("No ebXML TimeToLive"))
    }

    @Test
    fun `sendToRetryIfShouldBeRetried does not retry when ttl expired and returns MessageError`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers = mockk<Headers>()
        every { receiverRecord.headers() } returns headers
        every { headers.lastHeader("retryCount") } returns null

        val payload = createPayloadMessageWithTtl(Instant.now().minusSeconds(10))

        coEvery { cpaValidationService.validateOutgoingMessage(any()) } returns mockk(relaxed = true)
        coEvery { eventRegistrationService.registerEventMessageDetails(any()) } just Runs
        coEvery { handler.returnMessageError(any(), any()) } just Runs

        handler.sendToRetryIfShouldBeRetried(receiverRecord, payload, EbmsException("fail"), "reason", Direction.IN)

        coVerify(exactly = 0) { handler.sendToRetryQueueIncoming(any(), any(), any()) }
        coVerify { handler.returnMessageError(any(), any()) }
    }

    @Test
    fun `sendToRetryIfShouldBeRetried retries when ttl in future`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers2 = mockk<Headers>()
        every { receiverRecord.headers() } returns headers2
        every { headers2.lastHeader("retryCount") } returns RecordHeader("retryCount", "1".toByteArray())

        val payload = createPayloadMessageWithTtl(Instant.now().plusSeconds(3600))

        coEvery { handler.returnMessageError(any(), any()) } just Runs

        handler.sendToRetryIfShouldBeRetried(receiverRecord, payload, EbmsException("fail"), "reason", Direction.IN)
        coVerify(exactly = 1) { handler.sendToRetryQueueIncoming(any(), any(), any()) }
    }

    @Test
    fun `sendToRetryIfShouldBeRetried retries when ttl is null`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers3 = mockk<Headers>()
        every { receiverRecord.headers() } returns headers3
        every { headers3.lastHeader("retryCount") } returns null

        val payload = createPayloadMessageWithTtl(null)

        coEvery { handler.returnMessageError(any(), any()) } just Runs

        handler.sendToRetryIfShouldBeRetried(receiverRecord, payload, EbmsException("fail"), "reason", Direction.IN)
        coVerify(exactly = 1) { handler.sendToRetryQueueIncoming(any(), any()) }
    }

    private fun createPayloadMessageWithTtl(ttl: Instant?) = PayloadMessage(
        requestId = Uuid.random().toString(),
        messageId = Uuid.random().toString(),
        conversationId = Uuid.random().toString(),
        cpaId = "cpa",
        addressing = Addressing(
            to = Party(listOf(PartyId(type = "t", value = "v")), role = "r"),
            from = Party(listOf(PartyId(type = "t2", value = "v2")), role = "r2"),
            service = "s",
            action = "a"
        ),
        payload = EbmsAttachment(bytes = byteArrayOf(), contentType = ""),
        document = null,
        refToMessageId = null,
        sentAt = Instant.now(),
        timeToLive = ttl,
        duplicateElimination = false,
        ackRequested = false
    )
}
