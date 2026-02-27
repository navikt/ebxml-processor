package no.nav.emottak.ebms.async.processing

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.SmtpTransportClient
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.message.model.AsyncPayload
import org.apache.kafka.common.header.internals.RecordHeaders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class MessageFilterServiceTest {

    val payloadMessageService = mockk<PayloadMessageService>()
    val signalMessageService = mockk<SignalMessageService>()
    val smtpTransportClient = mockk<SmtpTransportClient>()
    val eventRegistrationService = EventRegistrationServiceFake()
    val messageFilterService = MessageFilterService(
        payloadMessageService,
        signalMessageService,
        smtpTransportClient,
        eventRegistrationService
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        coEvery { payloadMessageService.process(any(), any()) } returns Unit
        coEvery { signalMessageService.processSignal(any(), any()) } returns Unit
    }

    @Test
    fun `Not ebxml message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/dokument.xml")

        val record = mockk<ReceiverRecord<String, ByteArray>>()

        every { record.key() } returns Uuid.random().toString()
        every { record.value() } returns message!!.readAllBytes()
        every { record.headers() } returns RecordHeaders()

        val exception = assertThrows<RuntimeException> {
            runBlocking {
                messageFilterService.filterMessage(record)
            }
        }
        assertEquals("Message does not contain ebXML message header", exception.message)
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
}

fun createAsyncPayload() = AsyncPayload(
    referenceId = Uuid.random(),
    contentId = Uuid.random().toString(),
    contentType = "text/xml",
    content = byteArrayOf()
)
