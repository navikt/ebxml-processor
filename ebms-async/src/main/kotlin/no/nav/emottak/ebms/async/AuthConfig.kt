package no.nav.emottak.ebms.async

import no.nav.emottak.utils.environment.getEnvVar
import no.nav.security.token.support.v3.IssuerConfig
import no.nav.security.token.support.v3.TokenSupportConfig

const val AZURE_AD_AUTH = "AZURE_AD"

private const val appName = "ebms-async"
private const val appScopeProperty = "EBMS_ASYNC_SCOPE"

class AuthConfig {

    companion object {
        fun getTokenSupportConfig(): TokenSupportConfig = TokenSupportConfig(
            IssuerConfig(
                name = AZURE_AD_AUTH,
                discoveryUrl = getAzureWellKnownUrl(),
                acceptedAudience = getAcceptedAudience()
            )
        )

        fun getScope(): String = getEnvVar(
            appScopeProperty,
            "api://${getEnvVar("NAIS_CLUSTER_NAME", "dev-fss")}.team-emottak.$appName/.default"
        )

        fun getAzureWellKnownUrl(): String = getEnvVar(
            "AZURE_APP_WELL_KNOWN_URL",
            "http://localhost:3344/${getEnvVar("AZURE_APP_TENANT_ID", AZURE_AD_AUTH)}/.well-known/openid-configuration"
        )

        fun getAzureTokenEndpoint(): String = getEnvVar(
            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
            "http://localhost:3344/$AZURE_AD_AUTH/token"
        )

        private fun getAcceptedAudience(): List<String> = listOf(getEnvVar("AZURE_APP_CLIENT_ID", getScope()))
    }
}
