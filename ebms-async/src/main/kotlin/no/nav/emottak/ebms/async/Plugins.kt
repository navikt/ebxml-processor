package no.nav.emottak.ebms.async

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.security.token.support.v3.tokenValidationSupport

internal fun Application.installMicrometerRegistry(appMicrometerRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}

internal fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}

internal fun Application.installAuthentication() {
    install(Authentication) {
        tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
    }
}
