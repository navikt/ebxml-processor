package no.nav.emottak.auth

import no.nav.emottak.util.getEnvVar
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig

const val AZURE_AD_AUTH = "AZURE_AD"

private const val appName = "ebms-send-in"
private const val appScopeProperty = "EBMS_SEND_IN_SCOPE"

class AuthConfig {

    companion object {
        fun getTokenSupportConfig(): TokenSupportConfig {
            return TokenSupportConfig(
                IssuerConfig(
                    name = AZURE_AD_AUTH,
                    discoveryUrl = getAzureWellKnownUrl(),
                    acceptedAudience = getAcceptedAudience()
                )
            )
        }

        fun getScope(): String {
            val cluster = getEnvVar("NAIS_CLUSTER_NAME", "dev-fss")
            return getEnvVar(appScopeProperty, "api://$cluster.team-emottak.$appName/.default")
        }

        fun getAzureWellKnownUrl(): String {
            val tenantId = getEnvVar("AZURE_APP_TENANT_ID", AZURE_AD_AUTH)
            return getEnvVar(
                "AZURE_APP_WELL_KNOWN_URL",
                "http://localhost:3344/$tenantId/.well-known/openid-configuration"
            )
        }

        fun getAzureTokenEndpoint(): String {
            return getEnvVar(
                "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
                "http://localhost:3344/$AZURE_AD_AUTH/token"
            )
        }

        private fun getAcceptedAudience(): List<String> {
            return listOf("default", getEnvVar("AZURE_APP_CLIENT_ID", getScope()))
        }
    }
}
