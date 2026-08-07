package no.nav.emottak.cpa.configuration

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * A (partial) representation of the OpenID Connect discovery document ("well-known configuration")
 * exposed by NHN's authorization server. Only the fields needed to authenticate are modeled here.
 */
@Serializable
data class NhnWellKnownConfiguration(
    val issuer: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String
)

/**
 * Fetches the OpenID discovery document at [NhnOAuthConfig.wellKnownUrl] and combines it with the
 * rest of the configured OAuth settings to produce a fully resolved [NhnOAuth], deriving [NhnOAuth.audience]
 * from the document's `issuer` and [NhnOAuth.tokenEndpoint] from its `token_endpoint`.
 */
suspend fun NhnOAuthConfig.resolve(httpClient: HttpClient): NhnOAuth {
    val discovery = httpClient.get(wellKnownUrl.toString()).body<NhnWellKnownConfiguration>()
    return NhnOAuth(
        keyId = keyId,
        clientId = clientId,
        audience = NhnOAuth.Audience(discovery.issuer),
        tokenEndpoint = URI(discovery.tokenEndpoint),
        scope = scope,
        grantType = grantType,
        clientAssertionType = clientAssertionType
    )
}
