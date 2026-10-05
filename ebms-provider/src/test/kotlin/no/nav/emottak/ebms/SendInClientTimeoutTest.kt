package no.nav.emottak.ebms

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import no.nav.emottak.util.jsonLenient
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.EbmsProcessing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.utils.common.model.SendInRequest
import no.nav.emottak.utils.common.model.SendInResponse
import no.nav.emottak.utils.environment.getEnvVar
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

private const val SEND_IN_DELAY_MILLIS = 500L
private const val TEST_DEFAULT_TIMEOUT_MILLIS = 100L
private const val TEST_LONG_RUNNING_TIMEOUT_MILLIS = 5_000L

class SendInClientTimeoutTest {

    @Test
    fun `Service with configured timeout uses extended request timeout`() = sendInTestApp { sendInClient ->
        val response = sendInClient.postSendInSynkron(sendInRequest("HarBorgerFrikortMengde"))
        assertEquals("HarBorgerFrikortMengde", response.addressing.service)
    }

    @Test
    fun `Service without configured timeout uses default request timeout`() = sendInTestApp { sendInClient ->
        assertThrows<HttpRequestTimeoutException> {
            sendInClient.postSendInSynkron(sendInRequest("HarBorgerEgenandelFritak"))
        }
    }

    private fun sendInTestApp(testBlock: suspend ApplicationTestBuilder.(SendInClient) -> Unit) = testApplication {
        val client = createClient {
            expectSuccess = true
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                jsonLenient()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = TEST_DEFAULT_TIMEOUT_MILLIS
            }
        }
        externalServices {
            hosts(getEnvVar("SEND_IN_URL", "http://ebms-send-in")) {
                install(ContentNegotiation) {
                    jsonLenient()
                }
                routing {
                    post("/fagmelding/synkron") {
                        delay(SEND_IN_DELAY_MILLIS.milliseconds)
                        call.respond(sendInResponse())
                    }
                }
            }
        }
        testBlock(SendInClient({ client }, mapOf("HarBorgerFrikortMengde" to TEST_LONG_RUNNING_TIMEOUT_MILLIS)))
    }

    private fun addressing(service: String) = Addressing(
        Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
        Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
        service,
        "action"
    )

    private fun sendInRequest(service: String) = SendInRequest(
        "messageId",
        "conversationId",
        "payloadId",
        "<xml/>".toByteArray(),
        addressing(service),
        "cpaId",
        EbmsProcessing(),
        null,
        "requestId"
    )

    private fun sendInResponse() = SendInResponse(
        "messageId",
        "refToMessageId",
        "conversationId",
        "cpaId",
        addressing("HarBorgerFrikortMengde"),
        "<xml/>".toByteArray(),
        requestId = "requestId"
    )
}
