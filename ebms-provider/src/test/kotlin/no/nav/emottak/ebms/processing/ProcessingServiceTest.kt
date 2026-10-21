package no.nav.emottak.ebms.processing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.PayloadProcessingClient
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ProcessingServiceTest {
    private val httpClient = HttpClient(MockEngine { error("No HTTP request expected") })
    private var failure: Exception = IllegalStateException("Processing failed")
    private val service = ProcessingService(
        object : PayloadProcessingClient({ httpClient }) {
            override suspend fun postPayloadRequest(payloadRequest: PayloadRequest): PayloadResponse = throw failure
        }
    )
    private val processing = PayloadProcessing(
        signingCertificate = SignatureDetails(byteArrayOf(), "signature-algorithm", "hash-function"),
        encryptionCertificate = byteArrayOf(),
        processConfig = ProcessConfig(
            kryptering = false,
            komprimering = false,
            signering = true,
            internformat = false,
            validering = false,
            apprec = false,
            ocspSjekk = false,
            juridiskLogg = false,
            adapter = null,
            errorAction = null
        )
    )
    private val message = PayloadMessage(
        requestId = "request-id",
        messageId = "message-id",
        conversationId = "conversation-id",
        cpaId = "cpa-id",
        addressing = Addressing(
            to = Party(listOf(PartyId("HER", "79768")), "Receiver"),
            from = Party(listOf(PartyId("HER", "123456")), "Sender"),
            service = "service",
            action = "action"
        ),
        payload = Payload("<payload/>".toByteArray(), "application/xml"),
        duplicateElimination = true
    )

    @AfterEach
    fun closeClient() {
        httpClient.close()
    }

    @Test
    fun `cancellation propagates unchanged for all processing entrypoints`() = runBlocking {
        val cancellation = CancellationException("Application shutting down")
        failure = cancellation

        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { service.processSyncIn(message, processing) }
        )
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { service.proccessSyncOut(message, processing) }
        )
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { service.processAsync(message, processing) }
        )
    }

    @Test
    fun `unexpected failures are still wrapped in EbmsException`() = runBlocking {
        val exception = assertFailsWith<EbmsException> { service.processAsync(message, processing) }

        assertSame(failure, exception.cause)
    }
}
