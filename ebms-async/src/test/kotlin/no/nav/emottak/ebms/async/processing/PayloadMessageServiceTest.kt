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
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.eventmanager.EventManagerService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.utils.kafka.model.EventType
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType
import org.w3c.dom.Document
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class PayloadMessageServiceTest {

    private lateinit var cpaValidationService: CPAValidationService
    private lateinit var processingService: ProcessingService
    private lateinit var ebmsSignalProducer: EbmsMessageProducer
    private lateinit var payloadMessageForwardingService: PayloadMessageForwardingService
    private lateinit var eventRegistrationService: EventRegistrationService
    private lateinit var eventManagerService: EventManagerService
    private lateinit var failedMessageQueue: FailedMessageKafkaHandler
    private lateinit var service: PayloadMessageService

    @BeforeEach
    fun setUp() {
        org.apache.xml.security.Init.init()
        clearAllMocks()
        cpaValidationService = mockk()
        processingService = mockk()
        ebmsSignalProducer = mockk()
        payloadMessageForwardingService = mockk()
        eventRegistrationService = mockk<EventRegistrationService>()
        eventManagerService = mockk<EventManagerService>()
        failedMessageQueue = mockk<FailedMessageKafkaHandler>()

        mockkStatic(EbmsDocument::signer)
        every {
            any<EbmsDocument>().signer(any())
        } returnsArgument(0)
    }

    private fun initService(enableSignalProducer: Boolean = true, enableRetryQueue: Boolean = true) {
        System.setProperty("EBMS_PAYLOAD_PRODUCER", "true")
        System.setProperty("EBMS_SIGNAL_PRODUCER", enableSignalProducer.toString())
        System.setProperty("EBMS_RETRY_QUEUE", enableRetryQueue.toString())
        service = PayloadMessageService(
            cpaValidationService,
            processingService,
            ebmsSignalProducer,
            payloadMessageForwardingService,
            eventRegistrationService,
            eventManagerService,
            failedMessageQueue
        )
    }

    @Test
    fun `process should stop processing if message is duplicate`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, fakeResult) = setupMocks(PerMessageCharacteristicsType.ALWAYS, true)

        service.process(mockk<ReceiverRecord<String, ByteArray>>(), payloadMessage)

        coVerify(exactly = 1) { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) }
        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 0) { processingService.processAsync(any(), any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(any()) }
        coVerify(exactly = 1) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertTrue(ebmsMessageSlots[0] is Acknowledgment)
        assertType<Acknowledgment>(ebmsMessageSlots, 0)
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 1) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue(fakeResult.isSuccess)
        coVerify(exactly = 1) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
    }

    @Test
    fun `process should process and forward message if not duplicate (IN)`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, fakeResult) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            direction = Direction.IN
        )

        service.process(mockk<ReceiverRecord<String, ByteArray>>(), payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 2) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        assertType<Acknowledgment>(ebmsMessageSlots, 1)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 1) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 0) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 1) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue(fakeResult.isSuccess)
        coVerify(exactly = 1) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
    }

    @Test
    fun `process should process and forward message if not duplicate (OUT)`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, fakeResult) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            direction = Direction.OUT
        )

        service.process(mockk<ReceiverRecord<String, ByteArray>>(), payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 2) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        assertType<Acknowledgment>(ebmsMessageSlots, 1)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 1) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 1) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue(fakeResult.isSuccess)
        coVerify(exactly = 1) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
    }

    @Test
    fun `process should return error message if EbmsException is thrown`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, fakeResult) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            processAsyncThrowsEbmsException = true
        )

        service.process(mockk<ReceiverRecord<String, ByteArray>>(), payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 2) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        assertType<MessageError>(ebmsMessageSlots, 1)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 0) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 1) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue(fakeResult.isSuccess)
        coVerify(exactly = 1) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
    }

    @Test
    fun `process should send to retry if SignatureException is thrown`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, _) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            processAsyncThrowsSignatureException = true
        )

        service.process(setupReceiverRecordAndFailedMessageQueueMock(), payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 1) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 0) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 0) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        coVerify(exactly = 0) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
        coVerify(exactly = 1) { failedMessageQueue.sendToRetry(any(), any(), any(), any()) }
    }

    @Test
    fun `process should send to retry if processPayloadMessage throws EbmsException and returnMessageError throws Exception`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, fakeResult) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            processAsyncThrowsEbmsException = true,
            validateOutgoingThrowsException = true
        )

        service.process(setupReceiverRecordAndFailedMessageQueueMock(), payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 2) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        assertType<MessageError>(ebmsMessageSlots, 1)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 0) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue(fakeResult.isSuccess)
        coVerify(exactly = 0) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
        coVerify(exactly = 1) { failedMessageQueue.sendToRetry(any(), any(), any(), any()) }
    }

    @Test
    fun `process should send to retry and rethrow Exception if its not EbmsException nor SignatureException`() = runBlocking {
        initService()
        val (payloadMessage, ebmsMessageSlots, fakeResult) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            validateOutgoingThrowsException = true
        )
        val resultException = assertFailsWith<Exception> {
            service.process(setupReceiverRecordAndFailedMessageQueueMock(), payloadMessage)
        }

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 2) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        assertType<Acknowledgment>(ebmsMessageSlots, 1)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 1) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 0) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue(fakeResult.isSuccess)
        coVerify(exactly = 0) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
        coVerify(exactly = 1) { failedMessageQueue.sendToRetry(any(), any(), any(), any()) }
        assertEquals("Unexpected exception", resultException.message)
    }

    @Test
    fun `sendResponseToTopic should not publish message if kafkaSignalProducer is not active`() = runBlocking {
        initService(enableSignalProducer = false)
        val (payloadMessage, ebmsMessageSlots, _) = setupMocks(PerMessageCharacteristicsType.ALWAYS, true)

        service.process(mockk<ReceiverRecord<String, ByteArray>>(), payloadMessage)

        coVerify(exactly = 1) { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) }
        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 0) { processingService.processAsync(any(), any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(any()) }
        coVerify(exactly = 1) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertTrue(ebmsMessageSlots[0] is Acknowledgment)
        coVerify(exactly = 1) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        coVerify(exactly = 0) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
    }

    @Test
    fun `process should not send to retry if SignatureException is thrown but kafkaErrorQueue is not active`() = runBlocking {
        initService(enableRetryQueue = false)
        val (payloadMessage, ebmsMessageSlots, _) = setupMocks(
            PerMessageCharacteristicsType.PER_MESSAGE,
            false,
            processAsyncThrowsSignatureException = true
        )

        service.process(setupReceiverRecordAndFailedMessageQueueMock(), payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 1) { eventRegistrationService.registerEventMessageDetails(any()) }
        assertType<PayloadMessage>(ebmsMessageSlots, 0)
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
        coVerify(exactly = 0) { payloadMessageForwardingService.returnMessageResponse(payloadMessage) }
        coVerify(exactly = 0) { cpaValidationService.validateOutgoingMessage(any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.runWithEvent(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        coVerify(exactly = 0) { ebmsSignalProducer.publishMessage(key = any(), value = any(), headers = any()) }
        coVerify(exactly = 0) { failedMessageQueue.sendToRetry(any(), any(), any(), any()) }
    }

    @Test
    fun `isDuplicateMessage returns true for PerMessage strategy with message duplicateElimination`() = runBlocking {
        initService(enableSignalProducer = false)
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        every { payloadMessage.duplicateElimination } returns true
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.PER_MESSAGE
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns true

        val result = service.isDuplicateMessage(payloadMessage)

        assertTrue(result)
    }

    @Test
    fun `isDuplicateMessage returns false for PerMessage strategy without message duplicateElimination`() = runBlocking {
        initService(enableSignalProducer = false)
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        every { payloadMessage.duplicateElimination } returns false
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.PER_MESSAGE

        val result = service.isDuplicateMessage(payloadMessage)

        assertFalse(result)
    }

    @Test
    fun `isDuplicateMessage returns true for ALWAYS strategy`() = runBlocking {
        initService(enableSignalProducer = false)
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.ALWAYS
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns true

        val result = service.isDuplicateMessage(payloadMessage)

        assertTrue(result)
    }

    @Test
    fun `isDuplicateMessage returns false for no duplicate strategy`() = runBlocking {
        initService(enableSignalProducer = false)
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.NEVER

        val result = service.isDuplicateMessage(payloadMessage)

        assertFalse(result)
    }

    private fun setupMocks(
        duplicateEliminationStrategy: PerMessageCharacteristicsType,
        isDuplicate: Boolean,
        direction: Direction = Direction.IN,
        processAsyncThrowsEbmsException: Boolean = false,
        processAsyncThrowsSignatureException: Boolean = false,
        validateOutgoingThrowsException: Boolean = false
    ): Triple<PayloadMessage, MutableList<EbmsMessage>, Result<RecordMetadata>> {
        val payloadMessage = createPayloadMessage()
        val ebmsMessageSlots = mutableListOf<EbmsMessage>()
        val fakeResult = Result.success(mockk<RecordMetadata>())
        val lambdaSlot = slot<(suspend () -> Result<RecordMetadata>)>()

        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns duplicateEliminationStrategy
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns isDuplicate
        coEvery { eventRegistrationService.registerEventMessageDetails(capture(ebmsMessageSlots)) } returns Unit
        coEvery { cpaValidationService.validateIncomingMessage(payloadMessage) } returns mockk<ValidationResult>(relaxed = true)

        if (validateOutgoingThrowsException) {
            coEvery { cpaValidationService.validateOutgoingMessage(any()) } throws Exception("Unexpected exception")
        } else {
            coEvery { cpaValidationService.validateOutgoingMessage(any()) } returns mockk(relaxed = true)
        }

        if (processAsyncThrowsEbmsException) {
            coEvery { processingService.processAsync(any(), any()) } throws EbmsException("Processing has failed")
        } else if (processAsyncThrowsSignatureException) {
            coEvery { processingService.processAsync(any(), any()) } throws SignatureException("Signering feilet")
        } else {
            coEvery { processingService.processAsync(any(), any()) } returns Pair(payloadMessage, direction)
        }

        coEvery { payloadMessageForwardingService.forwardMessageWithSyncResponse(any()) } just Runs
        coEvery { payloadMessageForwardingService.returnMessageResponse(any()) } just Runs
        coEvery { eventRegistrationService.registerEventMessageDetails(capture(ebmsMessageSlots)) } returns Unit
        coEvery { ebmsSignalProducer.publishMessage(any(), any(), any()) } returns fakeResult
        coEvery {
            eventRegistrationService.runWithEvent<Result<RecordMetadata>>(
                EventType.MESSAGE_PLACED_IN_QUEUE,
                EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                any(),
                any(),
                any(),
                any(),
                capture(lambdaSlot)
            )
        } coAnswers { lambdaSlot.captured() }
        return Triple(payloadMessage, ebmsMessageSlots, fakeResult)
    }

    private fun setupReceiverRecordAndFailedMessageQueueMock(): ReceiverRecord<String, ByteArray> {
        val receiverRecord = mockk<ReceiverRecord<String, ByteArray>>(relaxed = true)
        coEvery { receiverRecord.key() } returns "key"
        coEvery { receiverRecord.value() } returns "value".toByteArray()
        coEvery { failedMessageQueue.sendToRetry(any(), any(), any(), any()) } just Runs
        return receiverRecord
    }

    private inline fun <reified T : EbmsMessage> assertType(ebmsMessageSlots: MutableList<EbmsMessage>, i: Int) {
        assertTrue(
            ebmsMessageSlots[i] is T,
            "ebmsMessageSlots[$i] er ikke ${T::class.simpleName}, men ${ebmsMessageSlots[0].javaClass.name}"
        )
    }
}

fun createPayloadMessage(document: Document? = null) = PayloadMessage(
    requestId = Uuid.random().toString(),
    messageId = Uuid.random().toString(),
    conversationId = Uuid.random().toString(),
    cpaId = "123",
    addressing = createValidAddressing(),
    payload = EbmsAttachment(
        bytes = byteArrayOf(),
        contentType = ""
    ),
    document = document,
    refToMessageId = null,
    duplicateElimination = true
)

fun createValidAddressing() = Addressing(
    to = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "79768"
            )
        ),
        role = "Frikortregister"
    ),
    from = Party(
        listOf(
            PartyId(
                type = "HER",
                value = "123456"
            )
        ),
        role = "Behandler"
    ),
    service = "HarBorgerFrikortMengde",
    action = "EgenandelForesporsel"
)
