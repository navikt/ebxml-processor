package no.nav.emottak.ebms.async.processing

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.kafka.producer.EbmsMessageProducer
import no.nav.emottak.ebms.async.util.EventRegistrationService
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.eventmanager.EventManagerService
import no.nav.emottak.ebms.model.signer
import no.nav.emottak.ebms.processing.ProcessingService
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.EbmsAttachment
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType
import kotlin.test.assertFalse

class PayloadMessageServiceTest {

    private lateinit var cpaValidationService: CPAValidationService
    private lateinit var processingService: ProcessingService
    private lateinit var ebmsSignalProducer: EbmsMessageProducer
    private lateinit var payloadMessageForwardingService: PayloadMessageForwardingService
    private lateinit var eventRegistrationService: EventRegistrationService
    private lateinit var eventManagerService: EventManagerService
    private lateinit var service: PayloadMessageService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        cpaValidationService = mockk()
        processingService = mockk()
        ebmsSignalProducer = mockk()
        payloadMessageForwardingService = mockk()
        eventRegistrationService = EventRegistrationServiceFake()
        eventManagerService = mockk()
        service = PayloadMessageService(
            cpaValidationService,
            processingService,
            ebmsSignalProducer,
            payloadMessageForwardingService,
            eventRegistrationService,
            eventManagerService
        )

        mockkStatic(EbMSDocument::signer)
        every {
            any<EbMSDocument>().signer(any())
        } returnsArgument(0)
    }

    @Test
    fun `processPayloadMessage should stop processing if message is duplicate`() = runBlocking {
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        coEvery { cpaValidationService.validateIncomingMessage(payloadMessage) } returns mockk(relaxed = true)
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.ALWAYS
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns true

        service.processPayloadMessage(payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(any()) }
        coVerify(exactly = 0) { processingService.processAsync(any(), any()) }
        coVerify(exactly = 0) { payloadMessageForwardingService.forwardMessageWithSyncResponse(any()) }
    }

    @Test
    fun `processPayloadMessage should process and forward message if not duplicate`() = runBlocking {
        val payloadMessage = createPayloadMessage()
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.PER_MESSAGE
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns false
        coEvery { cpaValidationService.validateIncomingMessage(payloadMessage) } returns mockk<ValidationResult>(relaxed = true)
        coEvery { processingService.processAsync(any(), any()) } returns payloadMessage
        coEvery { payloadMessageForwardingService.forwardMessageWithSyncResponse(any()) } just Runs

        service.processPayloadMessage(payloadMessage)

        coVerify(exactly = 1) { eventManagerService.isDuplicateMessage(payloadMessage) }
        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(payloadMessage) }
        coVerify(exactly = 1) { processingService.processAsync(payloadMessage, any()) }
        coVerify(exactly = 1) { payloadMessageForwardingService.forwardMessageWithSyncResponse(payloadMessage) }
    }

    @Test
    fun `isDuplicateMessage returns true for PerMessage strategy with message duplicateElimination`() = runBlocking {
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        every { payloadMessage.duplicateElimination } returns true
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.PER_MESSAGE
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns true

        val result = service.isDuplicateMessage(payloadMessage)

        assertTrue(result)
    }

    @Test
    fun `isDuplicateMessage returns false for PerMessage strategy without message duplicateElimination`() = runBlocking {
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        every { payloadMessage.duplicateElimination } returns false
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.PER_MESSAGE

        val result = service.isDuplicateMessage(payloadMessage)

        assertFalse(result)
    }

    @Test
    fun `isDuplicateMessage returns true for ALWAYS strategy`() = runBlocking {
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.ALWAYS
        coEvery { eventManagerService.isDuplicateMessage(payloadMessage) } returns true

        val result = service.isDuplicateMessage(payloadMessage)

        assertTrue(result)
    }

    @Test
    fun `isDuplicateMessage returns false for no duplicate strategy`() = runBlocking {
        val payloadMessage = mockk<PayloadMessage>(relaxed = true)
        coEvery { cpaValidationService.getDuplicateEliminationStrategy(payloadMessage) } returns PerMessageCharacteristicsType.NEVER

        val result = service.isDuplicateMessage(payloadMessage)

        assertFalse(result)
    }
}

fun createPayloadMessage() = PayloadMessage(
    requestId = "",
    messageId = "",
    conversationId = "",
    cpaId = "",
    addressing = createValidAddressing(),
    payload = EbmsAttachment(
        bytes = byteArrayOf(),
        contentType = ""
    ),
    dokument = null,
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
