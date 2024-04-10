package no.nav.emottak.auth

import no.nav.emottak.util.getEnvVar
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig

const val AZURE_AD_AUTH = "AZURE_AD"

class AuthConfig {
    companion object {
        fun getEbmsSendInConfig(): TokenSupportConfig {
            return getTokenSupportConfig(getEbmsSendInScope())
        }

        fun getCpaRepoConfig(): TokenSupportConfig {
            return getTokenSupportConfig(getCpaRepoScope())
        }

        private fun getTokenSupportConfig(appScopeUri: String): TokenSupportConfig {
            return TokenSupportConfig(
                IssuerConfig(
                    name = AZURE_AD_AUTH,
                    discoveryUrl = getAzureAppWellKnownUrl(),
                    acceptedAudience = listOf("default", appScopeUri)
                )
            )
        }

        private fun getAzureAppWellKnownUrl(): String {
            val tenantId = getEnvVar("AZURE_APP_TENANT_ID", AZURE_AD_AUTH)
            return getEnvVar(
                "AZURE_APP_WELL_KNOWN_URL",
                "http://localhost:3344/$tenantId/.well-known/openid-configuration"
            )
        }

        fun getEbmsSendInScope(): String {
            val appName = "ebms-send-in"
            val cluster = getCluster()
            return getEnvVar("EBMS_SEND_IN_SCOPE", "api://$cluster.team-emottak.$appName/.default")
        }

        fun getCpaRepoScope(): String {
            val appName = "cpa-repo"
            val cluster = getCluster()
            return getEnvVar("CPA_REPO_SCOPE", "api://$cluster.team-emottak.$appName/.default")
        }


        private fun getCluster(): String {
            return getEnvVar("NAIS_CLUSTER_NAME", "dev-fss")
        }
    }
}

