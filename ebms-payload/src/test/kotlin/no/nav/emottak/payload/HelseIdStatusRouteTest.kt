package no.nav.emottak.payload

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

class HelseIdStatusRouteTest {
    @Test
    fun `returns OK when JWKS is reachable`() = statusApp {
        val response = client.get("/internal/status/helseid")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `returns service unavailable when JWKS cannot be reached`() =
        statusApp({ throw SocketTimeoutException("Read timed out") }) {
            val response = client.get("/internal/status/helseid")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }

    private fun statusApp(
        helseIdConnectionCheck: () -> Unit = {},
        testBlock: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            routing {
                registerHealthEndpoints(
                    PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                    helseIdConnectionCheck
                )
            }
        }
        testBlock()
    }
}
