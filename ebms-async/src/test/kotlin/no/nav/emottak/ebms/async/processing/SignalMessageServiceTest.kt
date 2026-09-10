package no.nav.emottak.ebms.async.processing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.REF_TO_MESSAGE_ID_NOT_SET
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.message.xml.createDocument
import no.nav.emottak.utils.common.parseOrGenerateUuid
import no.nav.emottak.utils.kafka.model.EventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class SignalMessageServiceTest {

    val cpaValidationService = mockk<CPAValidationService>()
    val messagePendingAckRepository = mockk<MessagePendingAckRepository>()
    val eventRegistrationService = spyk(EventRegistrationServiceFake())
    val signalMessageService = SignalMessageService(cpaValidationService, eventRegistrationService, messagePendingAckRepository)

    private fun acknowledgment(requestId: String): Acknowledgment = runBlocking {
        val document = this@SignalMessageServiceTest::class.java.classLoader
            .getResourceAsStream("signaltest/acknowledgment.xml")!!.readAllBytes().createDocument()
        EbmsDocument(requestId = requestId, document = document, attachments = emptyList())
            .transform() as Acknowledgment
    }

    private fun messageError(requestId: String): MessageError = runBlocking {
        val document = this@SignalMessageServiceTest::class.java.classLoader
            .getResourceAsStream("signaltest/messageerror.xml")!!.readAllBytes().createDocument()
        EbmsDocument(requestId = requestId, document = document, attachments = emptyList())
            .transform() as MessageError
    }

    @Test
    fun `Acknowledgment message processed successfully`() {
        val requestId = Uuid.random().toString()
        val acknowledgment = acknowledgment(requestId)
        val validationResult = ValidationResult()

        coEvery {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        } returns validationResult
        every {
            cpaValidationService.validateResult(validationResult, acknowledgment, checkSignature = true)
        } returns validationResult
        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        every { messagePendingAckRepository.wasAckSignatureRequested(any()) } returns true
        coEvery { messagePendingAckRepository.registerAckForMessage(any()) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, acknowledgment)
        }

        verify(exactly = 1) {
            cpaValidationService.validateResult(validationResult, acknowledgment, checkSignature = true)
        }
        coVerify(exactly = 1) { eventRegistrationService.registerEventMessageDetails(acknowledgment) }
        coVerify(exactly = 1) { messagePendingAckRepository.registerAckForMessage(acknowledgment.refToMessageId) }
        coVerify(exactly = 0) {
            eventRegistrationService.registerEvent(
                eventType = EventType.SIGNATURE_CHECK_FAILED,
                requestId = any(),
                contentId = any(),
                messageId = any(),
                eventData = any(),
                conversationId = any()
            )
        }
    }

    @Test
    fun `Acknowledgment is not signature validated when signed Ack was not requested`() {
        val requestId = Uuid.random().toString()
        val acknowledgment = acknowledgment(requestId)
        val validationResult = ValidationResult()

        coEvery {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        } returns validationResult
        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        every { messagePendingAckRepository.wasAckSignatureRequested(acknowledgment.refToMessageId) } returns false
        coEvery { messagePendingAckRepository.registerAckForMessage(any()) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, acknowledgment)
        }

        coVerify(exactly = 1) {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        }
        verify(exactly = 0) { cpaValidationService.validateResult(any(), any(), checkSignature = true) }
        coVerify(exactly = 1) { messagePendingAckRepository.registerAckForMessage(acknowledgment.refToMessageId) }
    }

    @Test
    fun `Acknowledgment processing continues when signature validation fails`() {
        val requestId = Uuid.random().toString()
        val acknowledgment = acknowledgment(requestId)
        val validationResult = ValidationResult()

        coEvery {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        } returns validationResult
        every {
            cpaValidationService.validateResult(validationResult, acknowledgment, checkSignature = true)
        } throws EbmsException(listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signeringsfeil: 0 signaturer i dokumentet")))
        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        every { messagePendingAckRepository.wasAckSignatureRequested(any()) } returns true
        coEvery { messagePendingAckRepository.registerAckForMessage(any()) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, acknowledgment)
        }

        coVerify(exactly = 1) { messagePendingAckRepository.registerAckForMessage(acknowledgment.refToMessageId) }
        coVerify(exactly = 1) {
            eventRegistrationService.registerEvent(
                eventType = EventType.SIGNATURE_CHECK_FAILED,
                requestId = requestId.parseOrGenerateUuid(),
                contentId = "",
                messageId = acknowledgment.refToMessageId,
                eventData = any(),
                conversationId = acknowledgment.conversationId
            )
        }
    }

    @Test
    fun `Unexpected failure during signature validation is not treated as a signature failure`() {
        val requestId = Uuid.random().toString()
        val acknowledgment = acknowledgment(requestId)
        val validationResult = ValidationResult()

        coEvery {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        } returns validationResult
        every {
            cpaValidationService.validateResult(validationResult, acknowledgment, checkSignature = true)
        } throws NullPointerException("payloadProcessing was null")
        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        every { messagePendingAckRepository.wasAckSignatureRequested(any()) } returns true
        coEvery { messagePendingAckRepository.registerAckForMessage(any()) } returns mockk(relaxed = true)

        assertThrows<NullPointerException> {
            runBlocking {
                signalMessageService.processSignal(requestId, acknowledgment)
            }
        }

        coVerify(exactly = 0) { messagePendingAckRepository.registerAckForMessage(any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.registerEvent(
                eventType = EventType.SIGNATURE_CHECK_FAILED,
                requestId = any(),
                contentId = any(),
                messageId = any(),
                eventData = any(),
                conversationId = any()
            )
        }
    }

    @Test
    fun `Acknowledgment processing aborts when CPA validation fails`() {
        val requestId = Uuid.random().toString()
        val acknowledgment = acknowledgment(requestId)

        coEvery {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        } throws EbmsException(listOf(Feil(ErrorCode.NOT_SUPPORTED, "CPA not found")))
        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        every { messagePendingAckRepository.wasAckSignatureRequested(any()) } returns true
        coEvery { messagePendingAckRepository.registerAckForMessage(any()) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, acknowledgment)
        }

        coVerify(exactly = 0) { messagePendingAckRepository.registerAckForMessage(any()) }
    }

    @Test
    fun `Acknowledgment processing aborts when CPA validation fails with a security failure`() {
        val requestId = Uuid.random().toString()
        val acknowledgment = acknowledgment(requestId)

        coEvery {
            cpaValidationService.validateIncomingMessage(acknowledgment, checkSignature = false)
        } throws EbmsException(listOf(Feil(ErrorCode.SECURITY_FAILURE, "Sertifikat er revokert")))
        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        every { messagePendingAckRepository.wasAckSignatureRequested(any()) } returns true
        coEvery { messagePendingAckRepository.registerAckForMessage(any()) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, acknowledgment)
        }

        verify(exactly = 0) { cpaValidationService.validateResult(any(), any(), any()) }
        coVerify(exactly = 0) { messagePendingAckRepository.registerAckForMessage(any()) }
    }

    @Test
    fun `MessageError processed successfully`() {
        val requestId = Uuid.random().toString()
        val messageError = messageError(requestId)
        val validationResult = ValidationResult()

        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        coEvery {
            cpaValidationService.validateIncomingMessage(messageError, checkSignature = false)
        } returns validationResult
        every {
            cpaValidationService.validateResult(validationResult, messageError, checkSignature = true)
        } returns validationResult

        runBlocking {
            signalMessageService.processSignal(requestId, messageError)
        }

        coVerify(exactly = 1) { eventRegistrationService.registerEventMessageDetails(messageError) }
        coVerify(exactly = messageError.feil.size) {
            eventRegistrationService.registerEvent(
                eventType = EventType.UNKNOWN_ERROR_OCCURRED,
                requestId = any(),
                contentId = any(),
                messageId = messageError.refToMessageId,
                eventData = any(),
                conversationId = messageError.conversationId
            )
        }
    }

    @Test
    fun `MessageError with missing RefToMessageId is resolved via cpaId and conversationId`() {
        val requestId = Uuid.random().toString()
        val originalMessageError = messageError(requestId)
        val resolvedMessageId = Uuid.random().toString()
        val messageError = originalMessageError.copy(refToMessageId = REF_TO_MESSAGE_ID_NOT_SET)
        val validationResult = ValidationResult()

        every {
            messagePendingAckRepository.findPendingMessageIdByCpaAndConversationId(messageError.cpaId, messageError.conversationId)
        } returns resolvedMessageId
        every { messagePendingAckRepository.existsForMessageId(resolvedMessageId) } returns true
        coEvery {
            cpaValidationService.validateIncomingMessage(messageError, checkSignature = false)
        } returns validationResult
        every {
            cpaValidationService.validateResult(validationResult, messageError, checkSignature = true)
        } returns validationResult

        runBlocking {
            signalMessageService.processSignal(requestId, messageError)
        }

        coVerify(exactly = 1) {
            eventRegistrationService.registerEventMessageDetails(messageError.copy(refToMessageId = resolvedMessageId))
        }
        coVerify(exactly = messageError.feil.size) {
            eventRegistrationService.registerEvent(
                eventType = EventType.UNKNOWN_ERROR_OCCURRED,
                requestId = any(),
                contentId = any(),
                messageId = resolvedMessageId,
                eventData = any(),
                conversationId = messageError.conversationId
            )
        }
    }

    @Test
    fun `MessageError with missing RefToMessageId is ignored when no pending message can be uniquely resolved`() {
        val requestId = Uuid.random().toString()
        val originalMessageError = messageError(requestId)
        val messageError = originalMessageError.copy(refToMessageId = REF_TO_MESSAGE_ID_NOT_SET)

        every {
            messagePendingAckRepository.findPendingMessageIdByCpaAndConversationId(messageError.cpaId, messageError.conversationId)
        } returns null

        runBlocking {
            signalMessageService.processSignal(requestId, messageError)
        }

        coVerify(exactly = 0) { eventRegistrationService.registerEventMessageDetails(any()) }
        coVerify(exactly = 0) {
            cpaValidationService.validateIncomingMessage(any(), checkSignature = any())
        }
        verify(exactly = 0) { messagePendingAckRepository.existsForMessageId(any()) }
    }

    @Test
    fun `MessageError contents are still reported when signature validation fails`() {
        val requestId = Uuid.random().toString()
        val messageError = messageError(requestId)
        val validationResult = ValidationResult()

        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        coEvery {
            cpaValidationService.validateIncomingMessage(messageError, checkSignature = false)
        } returns validationResult
        every {
            cpaValidationService.validateResult(validationResult, messageError, checkSignature = true)
        } throws EbmsException(listOf(Feil(ErrorCode.SECURITY_FAILURE, "Signeringsfeil: 0 signaturer i dokumentet")))

        runBlocking {
            signalMessageService.processSignal(requestId, messageError)
        }

        coVerify(exactly = 1) {
            eventRegistrationService.registerEvent(
                eventType = EventType.SIGNATURE_CHECK_FAILED,
                requestId = any(),
                contentId = "",
                messageId = messageError.refToMessageId,
                eventData = any(),
                conversationId = messageError.conversationId
            )
        }
        coVerify(exactly = messageError.feil.size) {
            eventRegistrationService.registerEvent(
                eventType = EventType.UNKNOWN_ERROR_OCCURRED,
                requestId = any(),
                contentId = any(),
                messageId = messageError.refToMessageId,
                eventData = any(),
                conversationId = messageError.conversationId
            )
        }
    }

    @Test
    fun `MessageError processing aborts when CPA validation fails`() {
        val requestId = Uuid.random().toString()
        val messageError = messageError(requestId)

        every { messagePendingAckRepository.existsForMessageId(any()) } returns true
        coEvery {
            cpaValidationService.validateIncomingMessage(messageError, checkSignature = false)
        } throws EbmsException(listOf(Feil(ErrorCode.NOT_SUPPORTED, "CPA not found")))

        runBlocking {
            signalMessageService.processSignal(requestId, messageError)
        }

        verify(exactly = 0) { cpaValidationService.validateResult(any(), any(), any()) }
        coVerify(exactly = 0) {
            eventRegistrationService.registerEvent(
                eventType = EventType.UNKNOWN_ERROR_OCCURRED,
                requestId = any(),
                contentId = any(),
                messageId = any(),
                eventData = any(),
                conversationId = any()
            )
        }
    }

    @Test
    fun `Payload message throws error`() {
        val document = runBlocking {
            this::class.java.classLoader
                .getResourceAsStream("signaltest/payloadmessage.xml")!!.readAllBytes().createDocument()
        }

        val requestId = Uuid.random().toString()
        val ebmsMessage = EbmsDocument(
            requestId = requestId,
            document = document,
            attachments = listOf(mockk<Payload>())
        ).transform()

        val exception = assertThrows<RuntimeException> {
            runBlocking {
                signalMessageService.processSignal(requestId, ebmsMessage)
            }
        }
        assertEquals("Cannot process message as signal message: $requestId", exception.message)
    }
}
