package no.nav.emottak.ediadapter.server.model

import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class DpopTokens(
    val accessToken: DPoPAccessToken,
    val expiresAt: Instant,
    val bufferSeconds: Duration = 5.seconds
) {
    fun isExpired(): Boolean = Clock.System.now() >= (expiresAt - bufferSeconds)
}

fun TokenInfo.toDpopTokens(): DpopTokens =
    DpopTokens(
        accessToken = DPoPAccessToken(accessToken, expiresIn.toLong(), Scope()),
        expiresAt = Clock.System.now().plus(expiresIn.seconds)
    )
