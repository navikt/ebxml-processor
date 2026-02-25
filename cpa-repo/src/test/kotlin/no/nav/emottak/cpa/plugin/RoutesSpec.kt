package no.nav.emottak.cpa.plugin

import com.nimbusds.jwt.SignedJWT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.fullPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import no.nav.emottak.cpa.auth.AuthConfig.Companion.getTokenSupportConfig
import no.nav.emottak.cpa.config
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

private val log = KotlinLogging.logger {}

class RoutesSpec : StringSpec(
    {
        lateinit var mockOAuth2Server: MockOAuth2Server

        val getToken: (String) -> SignedJWT = { audience: String ->
            mockOAuth2Server.issueToken(
                issuerId = config().azureAuth.issuer.value,
                audience = audience,
                subject = "testUser"
            )
        }

        val invalidAudience = "api://dev-fss.cpa-repo.some-other-service/.default"

        beforeSpec {
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        }

        "GET /CommunicationParties with request herid returns Response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/api/v1/CommunicationParties/1"
                respond("""[{"herId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties/1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"herId": "1"}]"""
            }
        }

        "GET /CommunicationParties without request herid returns Response" {

            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties/")

                response.status shouldBe NotFound
                response.bodyAsText() shouldContain ""
            }
        }

        "GET /CommunicationParties returns EDI response with authentication" { // OK
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/api/v1/CommunicationParties/1"
                respond("""[{"herId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/CommunicationParties/1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"herId": "1"}]"""
            }
        }
    }
)

private fun ApplicationTestBuilder.createJsonEnabledClient(): HttpClient =
    createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }

private fun TestApplicationBuilder.installExternalRoutes(
    ediClient: HttpClient,
    useAuthentication: Boolean = false
) {
    install(ContentNegotiation) {
        json()
    }

    val issuer = config().azureAuth.issuer.value

    if (useAuthentication) {
        install(Authentication) {
            tokenValidationSupport(
                issuer,
                getTokenSupportConfig()
            )
        }
    }

    routing {
        val externalRoutes: Route.() -> Unit = { externalRoutes(ediClient) }

        if (useAuthentication) {
            authenticate(issuer, build = externalRoutes)
        } else {
            externalRoutes(this)
        }
    }
}

private fun fakeEdiClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient =
    HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }

        install(ClientContentNegotiation) {
            json()
        }
    }

private suspend fun HttpClient.getWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = config().azureAuth.appScope.value
): HttpResponse =
    get(url) {
        header(
            Authorization,
            "Bearer ${getToken(audience).serialize()}"
        )
    }

private fun base64EncodedDocument(): String =
    Base64.encode(
        """
            <MsgHead>
                <Body>hello world</Body>
            </MsgHead>
        """
            .trimIndent()
            .toByteArray(UTF_8)
    )
