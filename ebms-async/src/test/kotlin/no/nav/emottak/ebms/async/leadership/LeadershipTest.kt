package no.nav.emottak.ebms.async.leadership

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeadershipTest {
    @Test
    fun `elector changes and failures update leadership without retaining stale authority`() = runTest {
        var response = """{"name":"pod-1","last_update":"unused"}"""
        var status = HttpStatusCode.OK
        val client = HttpClient(MockEngine { respond(response, status) }) { expectSuccess = true }
        ElectorLeadership("pod-1", "http://elector", client).use { leadership ->
            leadership.refresh()
            assertTrue(leadership.isLeader)
            response = """{"name":"pod-2"}"""
            leadership.refresh()
            assertFalse(leadership.isLeader)
            response = """{"name":"pod-1"}"""
            leadership.refresh()
            assertTrue(leadership.isLeader)
            status = HttpStatusCode.ServiceUnavailable
            leadership.refresh()
            assertNull(leadership.leader.value)
            assertFalse(leadership.isLeader)
        }
    }

    @Test
    fun `malformed or invalid elector replies never grant leadership`() = runTest {
        var response = "invalid"
        val client = HttpClient(MockEngine { respond(response) })
        ElectorLeadership("pod-1", "http://elector", client).use { leadership ->
            leadership.refresh()
            assertNull(leadership.leader.value)
            response = """{"name":"../pod-1"}"""
            leadership.refresh()
            assertNull(leadership.leader.value)
        }
    }
}

internal class TestLeadership(override val podName: String, elected: String?) : Leadership {
    override val leader = MutableStateFlow(elected)
    override suspend fun watch(): Nothing = awaitCancellation()
}
