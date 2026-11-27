package no.nav.emottak.ebms.async.leadership

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import no.nav.emottak.message.model.Direction
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LeaderForwarderTest {
    @Test
    fun `follower forwards exactly once to elected pod and propagates response`() = testApplication {
        var forwarded = 0
        val transport = HttpClient(
            MockEngine { request ->
                forwarded++
                assertEquals("10.2.3.4", request.url.host)
                assertEquals(8080, request.url.port)
                assertEquals("/api/retry/incoming/limit/3", request.url.encodedPath)
                assertEquals("1", request.headers[LeaderForwarder.FORWARDED_HEADER])
                respond("leader rejected batch", HttpStatusCode.Conflict)
            }
        )
        val forwarder = LeaderForwarder(TestLeadership("follower", "leader"), { "http://10.2.3.4:8080" }, transport)
        application {
            routing {
                get("/trigger") {
                    if (!forwarder.forwardIfFollower(call, Direction.IN, 3)) call.respondText("local")
                }
            }
        }
        val response = client.get("/trigger")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("leader rejected batch", response.bodyAsText())
        assertEquals(1, forwarded)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        transport.close()
    }

    @Test
    fun `unknown or stale leader never produces forwarding loops or false success`() = testApplication {
        val leadership = TestLeadership("follower", null)
        val transport = HttpClient(MockEngine { error("Must not send") })
        val forwarder = LeaderForwarder(leadership, { error("Must not resolve") }, transport)
        application {
            routing { get("/trigger") { forwarder.forwardIfFollower(call, Direction.OUT, 1) } }
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/trigger").status)
        leadership.leader.value = "leader"
        assertEquals(
            HttpStatusCode.ServiceUnavailable,
            client.get("/trigger") { header(LeaderForwarder.FORWARDED_HEADER, "1") }.status
        )
        transport.close()
    }

    @Test
    fun `ambiguous transport failure is reported without retrying`() = testApplication {
        var attempts = 0
        val transport = HttpClient(
            MockEngine {
                attempts++
                throw IOException("Disconnected after request dispatch")
            }
        )
        val forwarder = LeaderForwarder(TestLeadership("follower", "leader"), { "http://10.2.3.4:8080" }, transport)
        application {
            routing { get("/trigger") { forwarder.forwardIfFollower(call, Direction.IN, 1) } }
        }
        val response = client.get("/trigger")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("may have started"))
        assertEquals(1, attempts)
        transport.close()
    }

    @Test
    fun `discovery rereads rotated token and validates pod identity and address`() = runTest {
        var token = "first-test-token"
        var body = pod()
        val tokens = mutableListOf<String?>()
        val transport = HttpClient(
            MockEngine { request ->
                assertEquals("/api/v1/namespaces/team-emottak/pods/pod-1", request.url.encodedPath)
                tokens.add(request.headers[HttpHeaders.Authorization])
                respond(body)
            }
        )
        val resolver = KubernetesLeaderPodAddress("team-emottak", "ebms-async", transport, { token }, "https://kubernetes.test")
        assertEquals("http://10.2.3.4:8080", resolver.resolve("pod-1"))
        token = "rotated-test-token"
        resolver.resolve("pod-1")
        assertEquals(listOf<String?>("Bearer first-test-token", "Bearer rotated-test-token"), tokens)
        for (invalid in listOf(
            pod(application = "other-app"),
            pod(ready = "False"),
            pod(ip = "127.0.0.1"),
            pod(ip = "attacker.example"),
            pod(deletion = ""","deletionTimestamp":"2026-01-01T00:00:00Z"""")
        )) {
            body = invalid
            assertFailsWith<IllegalArgumentException> { resolver.resolve("pod-1") }
        }
        transport.close()
    }

    private fun pod(application: String = "ebms-async", ready: String = "True", ip: String = "10.2.3.4", deletion: String = "") =
        """{"metadata":{"name":"pod-1","namespace":"team-emottak","labels":{"app":"$application"}$deletion},"spec":{"serviceAccountName":"ebms-async"},"status":{"podIP":"$ip","conditions":[{"type":"Ready","status":"$ready"}]}}"""
}
