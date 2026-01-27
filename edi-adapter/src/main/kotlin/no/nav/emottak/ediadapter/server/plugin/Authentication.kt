package no.nav.emottak.ediadapter.server.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import no.nav.emottak.ediadapter.server.auth.AuthConfig.Companion.getTokenSupportConfig
import no.nav.emottak.ediadapter.server.config
import no.nav.security.token.support.v3.tokenValidationSupport

fun Application.configureAuthentication() {
    install(Authentication) {
        tokenValidationSupport(
            config().azureAuth.issuer.value,
            getTokenSupportConfig()
        )
    }
}
