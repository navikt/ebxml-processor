package no.nav.emottak.ebms.async.processing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.xml.createDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

class SignalMessageServiceTest {

    val cpaValidationService = mockk<CPAValidationService>()
    val eventRegistrationService = EventRegistrationServiceFake()
    val signalMessageService = SignalMessageService(cpaValidationService, eventRegistrationService)

    @Test
    fun `Acknowledgment message processed successfully`() {
        val document = runBlocking {
            this::class.java.classLoader
                .getResourceAsStream("signaltest/acknowledgment.xml")!!.readAllBytes().createDocument()
        }

        val requestId = Uuid.random().toString()
        val acknowledgment = EbmsDocument(
            requestId = requestId,
            document = document,
            attachments = emptyList()
        ).transform() as Acknowledgment

        coEvery { cpaValidationService.validateIncomingMessage(acknowledgment) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, acknowledgment)
        }

        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(acknowledgment) }
        coVerify(exactly = 1) { signalMessageService.processAcknowledgment(acknowledgment) }
    }

    @Test
    fun `MessageError processed successfully`() {
        val document = runBlocking {
            this::class.java.classLoader
                .getResourceAsStream("signaltest/messageerror.xml")!!.readAllBytes().createDocument()
        }

        val requestId = Uuid.random().toString()
        val messageError = EbmsDocument(
            requestId = requestId,
            document = document,
            attachments = emptyList()
        ).transform() as MessageError

        coEvery { cpaValidationService.validateIncomingMessage(messageError) } returns mockk(relaxed = true)

        runBlocking {
            signalMessageService.processSignal(requestId, messageError)
        }

        coVerify(exactly = 1) { cpaValidationService.validateIncomingMessage(messageError) }
        coVerify(exactly = 1) { signalMessageService.processMessageError(messageError) }
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
