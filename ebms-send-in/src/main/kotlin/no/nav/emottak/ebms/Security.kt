package no.nav.emottak.ebms

import no.nav.emottak.util.getEnvVar
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig

const val AZURE_AD_AUTH = "AZURE_AD"

class Security {
    val TENANT_ID = getEnvVar("AZURE_APP_TENANT_ID", AZURE_AD_AUTH)
    val config = TokenSupportConfig(
        IssuerConfig(
            name = AZURE_AD_AUTH,
            discoveryUrl = getEnvVar(
                "AZURE_APP_WELL_KNOWN_URL",
                "http://localhost:3344/$TENANT_ID/.well-known/openid-configuration"
            ),
            optionalClaims = listOf("aud")

        )
    )
}

val CPA_REPO_SCOPE = getEnvVar(
    "CPA_REPO_SCOPE",
    "api://" + getEnvVar("NAIS_CLUSTER_NAME", "dev-fss") +
        ".team-emottak.cpa-repo/.default"
)

class Security {
    val TENANT_ID = getEnvVar("AZURE_APP_TENANT_ID", AZURE_AD_AUTH)
    val config = TokenSupportConfig(
        IssuerConfig(
            name = AZURE_AD_AUTH,
            discoveryUrl = getEnvVar(
                "AZURE_APP_WELL_KNOWN_URL",
                "http://localhost:3344/$TENANT_ID/.well-known/openid-configuration"
            ),
            optionalClaims = listOf("aud")
            // acceptedAudience = listOf("default", CPA_REPO_SCOPE)
        )
    )
}
