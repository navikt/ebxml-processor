package no.nav.emottak.cpa.AR

import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class DpopTokens(
    val accessToken: DPoPAccessToken,
    val expiresAt: Instant,
    val bufferSeconds: Duration = 5.seconds
) {
    fun isExpired(): Boolean = Clock.System.now() >= (expiresAt - bufferSeconds)
}

@OptIn(ExperimentalTime::class)
fun TokenInfo.toDpopTokens(): DpopTokens =
    DpopTokens(
        accessToken = DPoPAccessToken(accessToken, expiresIn.toLong(), Scope()),
        expiresAt = Clock.System.now().plus(expiresIn.seconds)
    )

@Serializable
data class TokenInfo(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("token_type")
    val tokenType: String
)
