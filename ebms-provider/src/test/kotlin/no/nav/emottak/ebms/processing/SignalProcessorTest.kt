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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import javax.xml.bind.UnmarshalException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SignalProcessorTest {

    val repository = mockk<EbmsMessageDetailsRepository>()
    val validator = mockk<DokumentValidator>()
    val signalProcessor = SignalProcessor(repository, validator)

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Process acknowledgment goes OK`() {
        every {
            repository.getByConversationIdMessageIdAndCpaId(any(), any(), any())
        } returns ebmsMessageDetails
        every {
            repository.saveEbmsMessageDetails(any())
        } returns Uuid.random()
        coEvery {
            validator.validateIn(any())
        } returns validationResult

        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/acknowledgment.xml")

        runBlocking {
            signalProcessor.processSignal(UUID.randomUUID().toString(), message!!.readAllBytes())
        }
    }

    @Test
    @Disabled
    fun `Payload message throws error`() {
        val message = this::class.java.classLoader
            .getResourceAsStream("signaltest/payloadmessage.xml")

        val exception = assertThrows<RuntimeException> {
            runBlocking {
                signalProcessor.processSignal(UUID.randomUUID().toString(), message!!.readAllBytes())
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
                signalProcessor.processSignal(UUID.randomUUID().toString(), message!!.readAllBytes())
            }
        }
    }
}

val validationResult = ValidationResult()

@OptIn(ExperimentalUuidApi::class)
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
