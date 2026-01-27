package no.nav.emottak.ediadapter.server.plugin

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.headersOf
import kotlinx.datetime.Clock
import no.nav.emottak.ediadapter.server.config
import no.nav.emottak.ediadapter.server.model.DpopTokens
import no.nav.emottak.ediadapter.server.util.DpopJwtProvider
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class DpopAuthSpec : StringSpec(
    {
        "should fetch token if none is cached and add headers" {
            val apiUrl = "https://api.test/Messages"

            val dummyTokens = DpopTokens(
                accessToken = DPoPAccessToken("access-token"),
                expiresAt = Clock.System.now().plus(60.seconds)
            )

            val mockEngine = MockEngine { request ->
                request.headers["Authorization"] shouldBe "DPoP access-token"
                val proof = request.headers["DPoP"]
                proof shouldNotBe null

                val signedJwt = SignedJWT.parse(proof)
                val claims = signedJwt.jwtClaimsSet
                claims.getStringClaim("htm") shouldBe Get.value
                claims.getStringClaim("htu") shouldBe apiUrl

                respond(
                    content = """{"ok":true}""",
                    status = OK,
                    headers = headersOf(ContentType, "application/json")
                )
            }

            val client = HttpClient(mockEngine) {
                install(DpopAuth) {
                    dpopJwtProvider = DpopJwtProvider(config())
                    loadTokens = { dummyTokens }
                }
            }

            val response = client.get(apiUrl)

            response.status shouldBe OK
        }
    }
)
