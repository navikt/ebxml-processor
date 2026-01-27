package no.nav.emottak.ediadapter.server.model

import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class DpopTokensSpec : StringSpec(
    {
        "should report expired when expiresAt is in the past" {
            val past = Clock.System.now().minus(10.seconds)
            val tokens = DpopTokens(
                accessToken = DPoPAccessToken("dummy"),
                expiresAt = past
            )

            tokens.isExpired() shouldBe true
        }

        "should report not expired when expiresAt is in the future" {
            val future = Clock.System.now().plus(120.seconds)
            val tokens = DpopTokens(
                accessToken = DPoPAccessToken("dummy"),
                expiresAt = future
            )

            tokens.isExpired() shouldBe false
        }

        "should report expired slightly before real expiry" {
            val nearFuture = Clock.System.now().plus(5.seconds) // smaller than buffer
            val tokens = DpopTokens(
                accessToken = DPoPAccessToken("dummy"),
                expiresAt = nearFuture
            )

            tokens.isExpired() shouldBe true
        }
    }
)
