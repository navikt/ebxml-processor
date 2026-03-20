package no.nav.emottak.ebms.async

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.emottak.util.jsonLenient
import no.nav.security.token.support.v3.tokenValidationSupport

internal fun Application.installMicrometerRegistry(appMicrometerRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}

internal fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jsonLenient()
    }
}

internal fun Application.installAuthentication() {
    install(Authentication) {
        tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
    }
}
