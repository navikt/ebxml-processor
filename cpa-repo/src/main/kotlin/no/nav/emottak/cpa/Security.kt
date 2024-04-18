package no.nav.emottak.cpa

import no.nav.emottak.util.getEnvVar
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig

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
            // optionalClaims = listOf("aud")
            acceptedAudience = listOf("default", getEnvVar("AZURE_APP_CLIENT_ID", CPA_REPO_SCOPE))
        )
    )
}

/*
* no.nav.security.jwt.issuer.[issuer shortname] - all properties relevant for a particular issuer must be listed under a short name for that issuer (not the actual issuer value from the token, but a chosen name to represent config for the actual issuer) you trust, e.g. citizen or employee
no.nav.security.jwt.issuer.[issuer shortname].discoveryurl - The identity provider configuration/discovery endpoint (metadata)
no.nav.security.jwt.issuer.[issuer shortname].accepted_audience - The value of the audience (aud) claim in the JWT token. For OIDC it is the client ID of the client responsible for acquiring the token, in OAuth 2.0 it should be the identifier for you api.
no.nav.security.jwt.issuer.[issuer shortname].validation.optional-claims - A comma separated list of optional claims that will be excluded from default claims.
no.nav.security.jwt.issuer.[issuer shortname].jwks-cache.lifespan - Cache the retrieved JWK keys to speed up subsequent look-ups. A non-negative lifespan expressed in minutes. (Default 15 min)
no.nav.security.jwt.issuer.[issuer shortname].jwks-cache.refreshtime - A non-negative refresh time expressed in minutes. (Default 5 min)
"Corporate" proxy support per issuer
Each issuer can be configured to use or not use a proxy by specifying the following properties:

no.nav.security.jwt.issuer.[issuer shortname].proxyurl - The full url of the proxy, e.g. http://proxyhost:8088
* */
