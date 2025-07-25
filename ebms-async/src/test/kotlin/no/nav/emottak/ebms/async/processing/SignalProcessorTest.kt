package no.nav.emottak.ebms.async.processing

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.validation.CPAValidationService
import no.nav.emottak.message.model.EbmsMessageDetails
import no.nav.emottak.message.model.ValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import javax.xml.bind.UnmarshalException
import kotlin.uuid.Uuid

class SignalProcessorTest {

    val repository = mockk<EbmsMessageDetailsRepository>()
    val cpaValidationService = mockk<CPAValidationService>()
    val signalMessageService = SignalMessageService(repository, cpaValidationService)

    @Test
    fun `Process acknowledgment goes OK`() {
        every {
            repository.getByConversationIdMessageIdAndCpaId(any(), any(), any())
        } returns ebmsMessageDetails
        every {
            repository.saveEbmsMessageDetails(any())
        } returns Uuid.random()
        coEvery {
            cpaValidationService.validateIncomingMessage(any())
        } returns validationResult

        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/acknowledgment.xml")

        runBlocking {
            signalMessageService.processSignal(UUID.randomUUID().toString(), message!!.readAllBytes())
        }
    }

    @Test
    @Disabled
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

val validationResult = ValidationResult()

val ebmsMessageDetails = EbmsMessageDetails(
    Uuid.random(),
    "123",
    "123",
    "123",
    "123",
    "123",
    "123",
    "123",
    "123",
    "123",
    "123"
)
