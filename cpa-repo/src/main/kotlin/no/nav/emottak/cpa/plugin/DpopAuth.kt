package no.nav.emottak.cpa.plugin

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.URLBuilder
import no.nav.emottak.cpa.model.DpopTokens
import no.nav.emottak.cpa.util.DpopJwtProvider
import java.net.URI

private const val QUERY_PARAMETERS = "?"

val DpopAuth = createClientPlugin("DpopAuth", ::DpopAuthConfig) {
    val config = pluginConfig
    val jwtProvider = config.dpopJwtProvider!!

    onRequest { request, _ ->
        if (config.tokens == null || config.tokens!!.isExpired()) {
            config.tokens = config.loadTokens?.invoke()
        }

        val dpopTokens = config.tokens!!

        val proof = jwtProvider.dpopProofWithTokenInfo(
            urlWithPath(request.url),
            request.method,
            dpopTokens.accessToken
        )

        val accessToken = dpopTokens.accessToken
        request.headers.append(Authorization, accessToken.toAuthorizationHeader())
        request.headers.append(accessToken.type.value, proof)
    }
}

class DpopAuthConfig {
    var dpopJwtProvider: DpopJwtProvider? = null
    var loadTokens: (suspend () -> DpopTokens)? = null
    var tokens: DpopTokens? = null
}

private fun urlWithPath(urlBuilder: URLBuilder): URI =
    URI
        .create(urlBuilder.toString().substringBefore(QUERY_PARAMETERS))
