package no.nav.emottak.ebms

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.Serializable

fun Routing.registerRootEndpoint() {
    get("/") {
        call.respond(StatusResponse(status = "Hello"))
    }
}
fun Routing.registerHealthEndpoints() {
    get("/internal/health/liveness") {
        call.respond(StatusResponse(status = "Alive"))
    }
    get("/internal/health/readiness") {
        call.respond(StatusResponse(status = "Ready"))
    }
}
fun Routing.registerPrometheusEndpoint(
    collectorRegistry: PrometheusMeterRegistry
) {
    get("/prometheus") {
        call.respond(collectorRegistry.scrape())
    }
}
fun Routing.registerNavCheckStatus() {
    get("/internal/status") {
        call.respond(StatusResponse(status = "OK"))
    }
}

@Serializable
data class StatusResponse(val status: String)
