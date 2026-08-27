package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.incrementFirstFailure
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.REASON_FORCED_RETRY
import no.nav.emottak.ebms.async.kafka.consumer.RETRY_REASON
import no.nav.emottak.ebms.async.model.MessageReceived
import no.nav.emottak.ebms.async.persistence.repository.MessageReceivedRepository
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.EbmsMessage
import org.apache.kafka.common.header.internals.RecordHeaders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class MessageFilterServiceTest {

    val payloadMessageService = mockk<PayloadMessageService>()
    val signalMessageService = mockk<SignalMessageService>()
    val smtpTransportClient = mockk<SmtpTransportClient>()
    val eventRegistrationService = EventRegistrationServiceFake()
    val failedMessageKafkaHandler = mockk<FailedMessageKafkaHandler>()
    val messageReceivedRepository = mockk<MessageReceivedRepository>()
    val messageFilterService = MessageFilterService(
        payloadMessageService,
        signalMessageService,
        smtpTransportClient,
        eventRegistrationService,
        failedMessageKafkaHandler,
        messageReceivedRepository
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        coEvery { payloadMessageService.process(any(), any()) } returns Unit
        coEvery { signalMessageService.processSignal(any(), any()) } returns Unit
    }

    @Test
    fun `Not ebxml adds message to error queue`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/dokument.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.headers() } returns RecordHeaders()
        coEvery { failedMessageKafkaHandler.sendToRetryQueueIncoming(record, any(), any()) } returns Unit
        coEvery { failedMessageKafkaHandler.meterRegistry.incrementFirstFailure(any(), any(), any()) } returns Unit

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        coVerify(exactly = 1) {
            failedMessageKafkaHandler.sendToRetryQueueIncoming(record, any(), any())
        }
    }

    @Test
    fun `Payload message processed as PayloadMessage`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.topic() } returns "topic"
        every { record.headers() } returns RecordHeaders()
        coEvery { smtpTransportClient.getPayload(any()) } returns listOf(createAsyncPayload())

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        coVerify(exactly = 1) {
            payloadMessageService.process(record, any())
        }
    }

    @Test
    fun `Payload message with forced retry header is processed with forceSkipDuplicateCheck true`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()
        val headers = RecordHeaders().add(RETRY_REASON, REASON_FORCED_RETRY.toByteArray())

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.topic() } returns "topic"
        every { record.headers() } returns headers
        coEvery { smtpTransportClient.getPayload(any()) } returns listOf(createAsyncPayload())
        coEvery { payloadMessageService.process(any(), any(), any()) } returns Unit

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        coVerify(exactly = 1) {
            payloadMessageService.process(record, any(), true)
        }
    }

    @Test
    fun `Payload message without forced retry header is processed with forceSkipDuplicateCheck false`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.topic() } returns "topic"
        every { record.headers() } returns RecordHeaders()
        coEvery { smtpTransportClient.getPayload(any()) } returns listOf(createAsyncPayload())
        coEvery { payloadMessageService.process(any(), any(), any()) } returns Unit

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        coVerify(exactly = 1) {
            payloadMessageService.process(record, any(), false)
        }
    }

    @Test
    fun `Acknowledgment message processed as signal message`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/acknowledgment.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.topic() } returns "topic"
        every { record.headers() } returns RecordHeaders()

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        coVerify(exactly = 1) {
            signalMessageService.processSignal(any(), any())
        }
    }

    @Test
    fun `Error message processed as signal message`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/messageerror.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.topic() } returns "topic"
        every { record.headers() } returns RecordHeaders()

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        coVerify(exactly = 1) {
            signalMessageService.processSignal(any(), any())
        }
    }

    @Test
    fun `Error message with missing refToMessageId processed as signal message, with looked-up refToMessageId`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/messageerror_withNoRefToMessageId.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()
        // Dette feiler ved bygg i github, ifølge copilot kan vi ikke mocke MessageReceived, må lage dummy objekt istedenfor
//        val incomingMessage: MessageReceived = mockk()
//        every { incomingMessage.messageId } returns "originalMessageId"
        val incomingMessage = dummyMessageReceived("originalMessageId")

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.topic() } returns "topic"
        every { record.headers() } returns RecordHeaders()
        // When looking up the original message in the conversation, you get a message with messageId = "originalMessageId"
        every { messageReceivedRepository.getFirstByConversationId("20140607-214220-751-0") } returns incomingMessage

        runBlocking {
            messageFilterService.filterMessage(record)
        }

        // The message should be processed as a signal message, and contain the looked-up refToMessageId
        val processedMessageSlot = slot<EbmsMessage>()
        coVerify(exactly = 1) {
            signalMessageService.processSignal(any(), capture(processedMessageSlot))
        }
        assertEquals("originalMessageId", processedMessageSlot.captured.refToMessageId)
    }
}

fun dummyMessageReceived(id: String): MessageReceived {
    return MessageReceived(
        referenceId = Uuid.random(),
        conversationId = "20140607-214220-751-0",
        messageId = id,
        refToMessageId = null,
        cpaId = "unknown",
        senderRole = "Frikortregister",
        senderId = "79768",
        receiverRole = "Behandler",
        receiverId = "987654321",
        service = "urn:oasis:names:tc:ebxml-msg:service",
        action = "MessageError",
        receivedAt = java.time.Instant.now(),
        acknowledged = false)
}

fun createAsyncPayload() = AsyncPayload(
    referenceId = Uuid.random(),
    contentId = Uuid.random().toString(),
    contentType = "text/xml",
    content = byteArrayOf()
)
