package no.nav.emottak.ebms.async.processing

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
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.asReceiverRecord
import no.nav.emottak.ebms.async.kafka.consumer.getRecords
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class RetryServiceTest {

    private lateinit var cpaValidationService: CPAValidationService
    private lateinit var eventRegistrationService: EventRegistrationService
    private lateinit var failedMessageQueue: FailedMessageKafkaHandler
    private lateinit var signalSender: suspend (EbmsDocument, List<no.nav.emottak.message.model.EmailAddress>) -> Unit
    private lateinit var retryService: RetryService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        cpaValidationService = mockk()
        eventRegistrationService = mockk<EventRegistrationService>()
        failedMessageQueue = mockk<FailedMessageKafkaHandler>()
        signalSender = mockk()
        coEvery { signalSender(any(), any()) } just Runs

        mockkStatic(EbmsDocument::signer)
        every {
            any<EbmsDocument>().signer(any())
        } returnsArgument(0)

        initService()
    }

    private fun initService() {
        System.setProperty("EBMS_PAYLOAD_PRODUCER", "true")
        System.setProperty("EBMS_SIGNAL_PRODUCER", "true")
        System.setProperty("EBMS_RETRY_QUEUE", "true")
        coEvery {
            failedMessageQueue.sendToRetryQueueIncoming(any(), any(), any())
        } just runs
        retryService = RetryService(
            cpaValidationService,
            eventRegistrationService,
            failedMessageQueue,
            signalSender
        )
    }

    @Test
    fun `isExpired returns true for past ttl`() {
        val past = Instant.now().minusSeconds(60)
        assertTrue(retryService.isExpired(past))
    }

    @Test
    fun `isExpired returns false for future ttl`() {
        val future = Instant.now().plusSeconds(60)
        assertFalse(retryService.isExpired(future))
    }

    @Test
    fun `isExpired returns true for now instant as well`() {
        val now = Instant.now()
        // isExpired uses Instant.now() internally, so passing a captured now should be considered expired (<= nowAtCall)
        assertTrue(retryService.isExpired(now))
    }

    @Test
    fun `decideRetry returns TTL_EXPIRED when ttl is in past`() {
        val past = Instant.now().minusSeconds(10)
        val (decision, reason) = retryService.decideRetry(ttl = past, retriedAlready = 0, maxRetries = 5)
        assertEquals(RetryService.RetryDecision.TTL_EXPIRED, decision)
        assertTrue(reason.contains("TimeToLive expired"))
    }

    @Test
    fun `decideRetry returns MAX_RETRIES_EXCEEDED when retriedAlready is at least maxRetries`() {
        val (decision, reason) = retryService.decideRetry(ttl = null, retriedAlready = 3, maxRetries = 3)
        assertEquals(RetryService.RetryDecision.MAX_RETRIES_EXCEEDED, decision)
        assertTrue(reason.contains("Retried too many times"))
    }

    @Test
    fun `decideRetry returns RETRY when ttl is present and not expired`() {
        val future = Instant.now().plusSeconds(3600)
        val (decision, reason) = retryService.decideRetry(ttl = future, retriedAlready = 1, maxRetries = 5)
        assertEquals(RetryService.RetryDecision.RETRY, decision)
        assertTrue(reason.contains("Within ebXML TimeToLive"))
    }

    @Test
    fun `decideRetry returns RETRY when ttl is null and retried less than max`() {
        val (decision, reason) = retryService.decideRetry(ttl = null, retriedAlready = 0, maxRetries = 5)
        assertEquals(RetryService.RetryDecision.RETRY, decision)
        assertTrue(reason.contains("No ebXML TimeToLive"))
    }

    @Test
    fun `decideRetry returns NO_RETRY when exception is unrecoverable`() {
        val unrecoverable = EbmsException("Dekryptering feilet", recoverable = false)
        val (decision, reason) = retryService.decideRetry(ttl = null, retriedAlready = 0, maxRetries = 5, throwable = unrecoverable)
        assertEquals(RetryService.RetryDecision.NO_RETRY, decision)
        assertTrue(reason.contains("EbmsException"))
    }

    @Test
    fun `decideRetry returns NO_RETRY for unrecoverable exception even when ttl is valid and retries remain`() {
        val future = Instant.now().plusSeconds(3600)
        val unrecoverable = EbmsException("Dekryptering feilet", recoverable = false)
        val (decision, _) = retryService.decideRetry(ttl = future, retriedAlready = 0, maxRetries = 5, throwable = unrecoverable)
        assertEquals(RetryService.RetryDecision.NO_RETRY, decision)
    }

    @Test
    fun `decideRetry does not return NO_RETRY for recoverable exception`() {
        val recoverable = EbmsException("Midlertidig feil", recoverable = true)
        val (decision, _) = retryService.decideRetry(ttl = null, retriedAlready = 0, maxRetries = 5, throwable = recoverable)
        assertEquals(RetryService.RetryDecision.RETRY, decision)
    }

    @Test
    fun `incomingRetryEval does not retry and returns MessageError when exception is unrecoverable`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers = mockk<Headers>()
        every { receiverRecord.headers() } returns headers
        every { headers.lastHeader("retryCount") } returns null

        val payload = createPayloadMessageWithTtl(Instant.now().plusSeconds(3600))
        val unrecoverable = EbmsException("Dekryptering feilet", recoverable = false)

        val spyService = spyk(retryService)
        coEvery { spyService.returnMessageError(any(), any()) } just Runs

        spyService.incomingRetryEval(receiverRecord, payload, unrecoverable)

        coVerify(exactly = 0) { failedMessageQueue.sendToRetryQueueIncoming(any(), any(), any()) }
        coVerify { spyService.returnMessageError(any(), unrecoverable) }
    }

    @Test
    fun `sendToRetryInIfShouldBeRetried does not retry when ttl expired and returns MessageError`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers = mockk<Headers>()
        every { receiverRecord.headers() } returns headers
        every { headers.lastHeader("retryCount") } returns null

        val payload = createPayloadMessageWithTtl(Instant.now().minusSeconds(10))

        coEvery { cpaValidationService.validateOutgoingMessage(any()) } returns mockk(relaxed = true)
        coEvery { eventRegistrationService.registerEventMessageDetails(any()) } just Runs

        val spyService = spyk(retryService)
        coEvery { spyService.returnMessageError(any(), any()) } just Runs

        spyService.incomingRetryEval(receiverRecord, payload, EbmsException("fail"), "reason")

        coVerify(exactly = 0) { failedMessageQueue.sendToRetryQueueIncoming(any(), any(), any()) }
        coVerify { spyService.returnMessageError(any(), any()) }
    }

    @Test
    fun `sendToRetryInIfShouldBeRetried retries when ttl in future`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers2 = mockk<Headers>()
        every { receiverRecord.headers() } returns headers2
        every { headers2.lastHeader("retryCount") } returns RecordHeader("retryCount", "1".toByteArray())

        val payload = createPayloadMessageWithTtl(Instant.now().plusSeconds(3600))

        val spyService = spyk(retryService)
        coEvery { spyService.returnMessageError(any(), any()) } just Runs
        coEvery { spyService.incomingRetryEval(any(), any(), any()) } just Runs

        spyService.incomingRetryEval(receiverRecord, payload, EbmsException("fail"), "reason")
        coVerify(exactly = 1) { failedMessageQueue.sendToRetryQueueIncoming(any(), any(), any()) }
    }

    @Test
    fun `sendToRetryInIfShouldBeRetried retries when ttl is null`() = runBlocking {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        val headers3 = mockk<Headers>()
        every { receiverRecord.headers() } returns headers3
        every { headers3.lastHeader("retryCount") } returns null

        val payload = createPayloadMessageWithTtl(null)

        val spyService = spyk(retryService)
        coEvery { spyService.returnMessageError(any(), any()) } just Runs
        coEvery { spyService.incomingRetryEval(any(), any(), any()) } just Runs

        spyService.incomingRetryEval(receiverRecord, payload, EbmsException("fail"), "reason")
        coVerify(exactly = 1) { failedMessageQueue.sendToRetryQueueIncoming(any(), any(), any()) }
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

    private fun makeRecordAtOffset(key: String, offset: Long): ReceiverRecord<String, ByteArray> =
        ConsumerRecord("topic", 0, offset, key, ByteArray(0)).asReceiverRecord()

    @AfterEach
    fun tearDownMocks() {
        unmockkAll()
    }

    // rerunUniqueKeysOutgoing

    @Test
    fun `rerunUniqueKeysOutgoing processes all records in a single batch`() = runBlocking {
        mockkStatic("no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandlerKt")
        val record1 = makeRecordAtOffset("key-1", 0L)
        val record2 = makeRecordAtOffset("key-2", 1L)
        every { getRecords(any(), any(), any(), any()) } answers {
            if (thirdArg<Long>() == 0L) listOf(record1, record2) else emptyList()
        }

        val processed = mutableListOf<ReceiverRecord<String, ByteArray>>()
        retryService.rerunUniqueKeysOutgoing(processor = { processed.add(it) })

        assertEquals(2, processed.size)
        assertEquals(setOf("key-1", "key-2"), processed.map { it.key() }.toSet())
    }

    @Test
    fun `rerunUniqueKeysOutgoing deduplicates by key, processing only the first occurrence`() = runBlocking {
        mockkStatic("no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandlerKt")
        val first = makeRecordAtOffset("same-key", 0L)
        val duplicate = makeRecordAtOffset("same-key", 1L)
        val unique = makeRecordAtOffset("other-key", 2L)
        every { getRecords(any(), any(), any(), any()) } answers {
            if (thirdArg<Long>() == 0L) listOf(first, duplicate, unique) else emptyList()
        }

        val processed = mutableListOf<ReceiverRecord<String, ByteArray>>()
        retryService.rerunUniqueKeysOutgoing(processor = { processed.add(it) })

        assertEquals(2, processed.size)
        assertEquals(1, processed.count { it.key() == "same-key" })
        assertEquals(first, processed.first { it.key() == "same-key" })
    }

    @Test
    fun `rerunUniqueKeysOutgoing starts fetching from startOffset`() = runBlocking {
        mockkStatic("no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandlerKt")
        every { getRecords(any(), any(), any(), any()) } answers {
            if (thirdArg<Long>() == 50L) listOf(makeRecordAtOffset("key-1", 50L)) else emptyList()
        }

        val processed = mutableListOf<ReceiverRecord<String, ByteArray>>()
        retryService.rerunUniqueKeysOutgoing(processor = { processed.add(it) }, startOffset = 50)

        assertEquals(1, processed.size)
        verify { getRecords(any(), any(), 50L, any()) }
    }

    @Test
    fun `rerunUniqueKeysOutgoing excludes records with offset beyond endOffset`() = runBlocking {
        mockkStatic("no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandlerKt")
        val withinRange = makeRecordAtOffset("key-1", 5L)
        val beyondRange = makeRecordAtOffset("key-2", 15L)
        every { getRecords(any(), any(), any(), any()) } answers {
            if (thirdArg<Long>() == 0L) listOf(withinRange, beyondRange) else emptyList()
        }

        val processed = mutableListOf<ReceiverRecord<String, ByteArray>>()
        retryService.rerunUniqueKeysOutgoing(processor = { processed.add(it) }, endOffset = 10)

        assertEquals(1, processed.size)
        assertEquals("key-1", processed[0].key())
    }

    @Test
    fun `rerunUniqueKeysOutgoing fetches next batch starting after last record in previous batch`() = runBlocking {
        mockkStatic("no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandlerKt")
        val firstBatch = (0 until 100).map { makeRecordAtOffset("key-$it", it.toLong()) }
        val secondBatch = listOf(makeRecordAtOffset("key-100", 100L), makeRecordAtOffset("key-101", 101L))
        every { getRecords(any(), any(), any(), any()) } answers {
            when (thirdArg<Long>()) {
                0L -> firstBatch
                100L -> secondBatch
                else -> emptyList()
            }
        }

        val processed = mutableListOf<ReceiverRecord<String, ByteArray>>()
        retryService.rerunUniqueKeysOutgoing(processor = { processed.add(it) })

        assertEquals(102, processed.size)
        verify { getRecords(any(), any(), 0L, any()) }
        verify { getRecords(any(), any(), 100L, any()) }
    }

    @Test
    fun `rerunUniqueKeysOutgoing does nothing when topic is empty`() = runBlocking {
        mockkStatic("no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandlerKt")
        every { getRecords(any(), any(), any(), any()) } returns emptyList()

        val processed = mutableListOf<ReceiverRecord<String, ByteArray>>()
        retryService.rerunUniqueKeysOutgoing(processor = { processed.add(it) })

        assertEquals(0, processed.size)
    }
}
