package no.nav.emottak.cpa.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import no.nav.emottak.cpa.auth.AuthConfig.Companion.getTokenSupportConfig
import no.nav.emottak.cpa.config
import no.nav.security.token.support.v3.tokenValidationSupport

fun Application.configureAuthentication() {
    install(Authentication) {
        tokenValidationSupport(
            config().azureAuth.issuer.value,
            getTokenSupportConfig()
        )
    }
}
