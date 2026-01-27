package no.nav.emottak.ediadapter.server.util

import com.nimbusds.oauth2.sdk.token.AccessTokenType.DPOP
import com.nimbusds.openid.connect.sdk.Nonce
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import no.nav.emottak.ediadapter.server.config.Config
import no.nav.emottak.ediadapter.server.model.DpopTokens
import no.nav.emottak.ediadapter.server.model.TokenInfo
import no.nav.emottak.ediadapter.server.model.toDpopTokens

class DpopTokenUtil(
    private val config: Config,
    private val jwtProvider: DpopJwtProvider,
    private val httpTokenClient: HttpClient
) {
    suspend fun obtainDpopTokens(): DpopTokens {
        val proofWithoutNonce = jwtProvider.dpopProofWithoutNonce()
        val tokenResponseWithoutNonce = tokenRequest(proofWithoutNonce)

        val response = when (tokenResponseWithoutNonce.status.value) {
            400 -> {
                val nonceHeader = tokenResponseWithoutNonce.headers["DPoP-Nonce"]
                    ?: error("DPoP-Nonce header missing")

                val proofWithNonce = jwtProvider.dpopProofWithNonce(Nonce(nonceHeader))
                tokenRequest(proofWithNonce)
            }

            else -> tokenResponseWithoutNonce
        }

        if (response.status.value != 200) {
            error("Failed to obtain DPoP token: ${response.status} - ${response.bodyAsText()}")
        }

        val tokenInfo: TokenInfo = response.body()
        return tokenInfo.toDpopTokens()
    }

    private suspend fun tokenRequest(dpopProofWithNonce: String): HttpResponse =
        httpTokenClient.post(config.nhnOAuth.tokenEndpoint.toString()) {
            header(DPOP.value, dpopProofWithNonce)
            contentType(FormUrlEncoded)
            setBody(
                Parameters.build {
                    append("client_id", config.nhnOAuth.clientId.value)
                    append("grant_type", config.nhnOAuth.grantType.value)
                    append("scope", config.nhnOAuth.scope.value)
                    append("client_assertion", jwtProvider.clientAssertion())
                    append("client_assertion_type", config.nhnOAuth.clientAssertionType.value)
                }
                    .formUrlEncode()
            )
        }
}
