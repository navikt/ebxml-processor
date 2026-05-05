package no.nav.emottak.ebms.async

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import no.nav.emottak.ebms.async.processing.PayloadMessageService
import no.nav.emottak.ebms.async.processing.RetryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RerunOutgoingRouteTest {

    private lateinit var retryService: RetryService
    private lateinit var payloadMessageService: PayloadMessageService
    private var savedRetryOutQueue: String? = null

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        savedRetryOutQueue = System.getProperty("EBMS_RETRY_OUT_QUEUE")
        retryService = mockk()
        payloadMessageService = mockk()
        coEvery { retryService.rerunUniqueKeysOutgoing(any(), any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        if (savedRetryOutQueue != null) {
            System.setProperty("EBMS_RETRY_OUT_QUEUE", savedRetryOutQueue!!)
        } else {
            System.clearProperty("EBMS_RETRY_OUT_QUEUE")
        }
    }

    private fun testApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                routing {
                    rerunOutgoing(retryService, payloadMessageService)
                }
            }
            block()
        }

    @Test
    fun `returns 503 when outgoing retry queue is not active`() {
        System.setProperty("EBMS_RETRY_OUT_QUEUE", "false")
        testApp {
            val response = client.get("/api/retry/outgoing/rerun/interval/")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }
    }

    @Test
    fun `returns 200 with default start and end offsets when no query params given`() {
        System.setProperty("EBMS_RETRY_OUT_QUEUE", "true")
        testApp {
            val response = client.get("/api/retry/outgoing/rerun/interval/")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("StartingOffset=0"))
            assertTrue(body.contains("EndOffset=${Int.MAX_VALUE - 1}"))
        }
    }

    @Test
    fun `returns 200 with specified start and end offsets in response text`() {
        System.setProperty("EBMS_RETRY_OUT_QUEUE", "true")
        testApp {
            val response = client.get("/api/retry/outgoing/rerun/interval/?start=10&end=500")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("StartingOffset=10"))
            assertTrue(body.contains("EndOffset=500"))
        }
    }

    @Test
    fun `calls rerunUniqueKeysOutgoing with parsed start and end offsets`() {
        System.setProperty("EBMS_RETRY_OUT_QUEUE", "true")
        testApp {
            client.get("/api/retry/outgoing/rerun/inteval/?start=20&end=200")
            coVerify { retryService.rerunUniqueKeysOutgoing(any<suspend (ReceiverRecord<String, ByteArray>) -> Unit>(), 20, 200) }
        }
    }
}
