package no.nav.emottak.ediadapter.server.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm.RSA256
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.DPoPAccessToken
import com.nimbusds.openid.connect.sdk.Nonce
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import no.nav.emottak.ediadapter.server.config
import java.net.URI
import java.util.Base64.getUrlDecoder
import kotlin.uuid.Uuid

class DpopJwtProviderSpec : StringSpec(
    {
        val jwtProvider = DpopJwtProvider(config())

        "should generate valid DPoP proof and required claims without nonce" {
            val signedJwtJson = jwtProvider.dpopProofWithoutNonce()

            val signedJwt = SignedJWT.parse(signedJwtJson)
            signedJwt.verify(RSASSAVerifier(jwtProvider.rsaKey())) shouldBe true

            val claims = signedJwt.jwtClaimsSet
            claims.getStringClaim("htm") shouldBe "POST"
            claims.getStringClaim("htu") shouldBe config().nhnOAuth.tokenEndpoint.toString()
            claims.getStringClaim("jti") shouldNotBe null
            claims.getDateClaim("iat") shouldNotBe null
        }

        "should generate valid DPoP proof and required claims with nonce" {
            val random = Uuid.random()
            val signedJwtJson = jwtProvider.dpopProofWithNonce(Nonce(random.toString()))

            val signedJwt = SignedJWT.parse(signedJwtJson)
            signedJwt.verify(RSASSAVerifier(jwtProvider.rsaKey())) shouldBe true

            val claims = signedJwt.jwtClaimsSet
            claims.getStringClaim("htm") shouldBe Post.value
            claims.getStringClaim("htu") shouldBe config().nhnOAuth.tokenEndpoint.toString()
            claims.getStringClaim("nonce") shouldBe random.toString()
            claims.getStringClaim("jti") shouldNotBe null
            claims.getDateClaim("iat") shouldNotBe null
        }

        "should generate valid DPoP proof and required claims with access token" {
            val uri = URI("https://my.uri.com")
            val accessToken = DPoPAccessToken("my access token")

            val signedJwtJson = jwtProvider.dpopProofWithTokenInfo(uri, Get, accessToken)

            val signedJwt = SignedJWT.parse(signedJwtJson)
            signedJwt.verify(RSASSAVerifier(jwtProvider.rsaKey())) shouldBe true

            val claims = signedJwt.jwtClaimsSet
            claims.getStringClaim("htm") shouldBe Get.value
            claims.getStringClaim("htu") shouldBe uri.toString()
            claims.getStringClaim("ath") shouldBe jwtProvider.accessTokenHash(accessToken.value)
            claims.getStringClaim("jti") shouldNotBe null
            claims.getDateClaim("iat") shouldNotBe null
        }

        "should generate a valid signed JWT client assertion" {
            val assertion = jwtProvider.clientAssertion()

            val decoded = JWT.decode(assertion)

            val verification = JWT.require(RSA256(jwtProvider.rsaKey().toRSAPublicKey(), null))
                .withIssuer(config().nhnOAuth.clientId.value)
                .withSubject(config().nhnOAuth.clientId.value)
                .withAudience(config().nhnOAuth.audience.value)
                .build()

            verification.verify(assertion)

            decoded.issuer shouldBe config().nhnOAuth.clientId.value
            decoded.subject shouldBe config().nhnOAuth.clientId.value
            decoded.audience shouldContain config().nhnOAuth.audience.value
            decoded.getClaim("jti").asString() shouldNotBe null

            val headerJson = String(getUrlDecoder().decode(decoded.header))
            headerJson shouldContain "\"kid\":\"${config().nhnOAuth.keyId.value}\""
            headerJson shouldContain "\"alg\":\"RS256\""
            headerJson shouldContain "\"typ\":\"client-authentication+jwt\""
        }
    }
)
