package no.nav.emottak.ebms.processing

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.validation.DokumentValidator
import no.nav.emottak.message.model.EbmsMessageDetails
import no.nav.emottak.message.model.ValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import javax.xml.bind.UnmarshalException

class SignalProcessorTest {

    val repository = mockk<EbmsMessageDetailsRepository>()
    val validator = mockk<DokumentValidator>()
    val signalProcessor = SignalProcessor(repository, validator)

    @Test
    fun `Process acknowledgment goes OK`() {
        every {
            repository.getByConversationIdMessageIdAndCpaId(any(), any(), any())
        } returns ebmsMessageDetails
        every {
            repository.saveEbmsMessageDetails(any())
        } returns UUID.randomUUID()
        coEvery {
            validator.validateIn(any())
        } returns validationResult

        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/acknowledgment.xml")

        runBlocking {
            signalProcessor.processSignal("123", message!!.readAllBytes())
        }
    }

    @Test
    fun `Payload message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val exception = assertThrows<RuntimeException> {
            runBlocking {
                signalProcessor.processSignal("123", message!!.readAllBytes())
            }
        }
        assertEquals("Unrecognized dokument type", exception.message)
    }

    @Test
    fun `Not ebxml message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/dokument.xml")

        assertThrows<UnmarshalException> {
            runBlocking {
                signalProcessor.processSignal("123", message!!.readAllBytes())
            }
        }
    }
}

val validationResult = ValidationResult()
val ebmsMessageDetails = EbmsMessageDetails(
    UUID.randomUUID(),
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
