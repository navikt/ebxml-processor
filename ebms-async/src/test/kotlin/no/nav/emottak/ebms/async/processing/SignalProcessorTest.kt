package no.nav.emottak.ebms.async.processing

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.xml.createDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class SignalProcessorTest {

    val cpaValidationService = mockk<CPAValidationService>()
    val eventRegistrationService = EventRegistrationServiceFake()
    val signalMessageService = SignalMessageService(cpaValidationService, eventRegistrationService)

    @Test
    fun `Payload message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val document = runBlocking {
            message!!.readAllBytes().createDocument()
        }

        val requestId = UUID.randomUUID().toString()
        val ebmsMessage = EbMSDocument(
            requestId = requestId,
            dokument = document,
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
