package no.nav.emottak.ebms.async.processing

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.validation.CPAValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import javax.xml.bind.UnmarshalException

class SignalProcessorTest {

    val cpaValidationService = mockk<CPAValidationService>()
    val eventRegistrationService = EventRegistrationServiceFake()
    val signalMessageService = SignalMessageService(cpaValidationService, eventRegistrationService)

    @Test
    fun `Payload message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val exception = assertThrows<RuntimeException> {
            runBlocking {
                signalMessageService.processSignal(UUID.randomUUID().toString(), message!!.readAllBytes())
            }
        }
        assertEquals("Unrecognized dokument type", exception.message)
    }

    @Test
    @Disabled
    fun `Not ebxml message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/dokument.xml")

        assertThrows<UnmarshalException> {
            runBlocking {
                signalMessageService.processSignal(UUID.randomUUID().toString(), message!!.readAllBytes())
            }
        }
    }
}
