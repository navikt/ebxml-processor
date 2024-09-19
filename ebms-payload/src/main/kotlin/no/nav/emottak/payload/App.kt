package no.nav.emottak.payload

import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.util.getEnvVar
import no.nav.security.token.support.v2.tokenValidationSupport
import org.slf4j.LoggerFactory


internal val log = LoggerFactory.getLogger("no.nav.emottak.payload")
fun main() {
    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    }
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = payloadApplicationModule()
    ).start(wait = true)
}

fun defaultHttpClient(): () -> HttpClient {
    return {
        HttpClient(CIO) {
            expectSuccess = true
        }
    }
}

fun payloadApplicationModule(): Application.() -> Unit {
    return {
        install(ContentNegotiation) {
            json()
        }
        val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
        install(Authentication) {
            tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
        }

        routing {
            registerHealthEndpoints(appMicrometerRegistry)

            authenticate(AZURE_AD_AUTH) {
                postPayload()
            }
        }
    }
}
