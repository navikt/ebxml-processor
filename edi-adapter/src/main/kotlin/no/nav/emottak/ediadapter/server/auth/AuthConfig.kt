package no.nav.emottak.ediadapter.server.auth

import no.nav.emottak.ediadapter.server.config
import no.nav.security.token.support.v3.IssuerConfig
import no.nav.security.token.support.v3.TokenSupportConfig

class AuthConfig {
    companion object {
        fun getTokenSupportConfig(): TokenSupportConfig = TokenSupportConfig(
            IssuerConfig(
                name = config().azureAuth.issuer.value,
                discoveryUrl = config().azureAuth.azureWellKnownUrl.value,
                acceptedAudience = listOf(config().azureAuth.acceptedAudience.value)
            )
        )
    }
}
