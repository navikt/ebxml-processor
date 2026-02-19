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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.contentType
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

        "GET /CommunicationParties with single receiver her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/CommunicationParties?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties?receiverHerIds=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /CommunicationParties with multiple receiver her ids returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/CommunicationParties?ReceiverHerIds=1&ReceiverHerIds=2"
                respond("""[{"id":"100", "receiverHerId": "1"}, {"id":"200", "receiverHerId": "2"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties?receiverHerIds=1&receiverHerIds=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}, {"id":"200", "receiverHerId": "2"}]"""
            }
        }

        "GET /communicationparties with receiver her id and sender her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/CommunicationParties?ReceiverHerIds=1&SenderHerId=2"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties?receiverHerIds=1&senderHerId=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /communicationparties with receiver her id and business document id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/CommunicationParties?ReceiverHerIds=1&BusinessDocumentId=10"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties?receiverHerIds=1&businessDocumentId=10")

                response.status shouldBe OK
                log.info { "$response.bodyAsText()" }
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /communicationparties with blank receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties?receiverHerIds=")
                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /communicationparties without receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/CommunicationParties")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "POST /CommunicationParties with empty body returns 415" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/CommunicationParties") {
                    contentType(Json)
                    setBody("")
                }

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /CommunicationParties without body returns 415" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/CommunicationParties")
                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /CommunicationParties with invalid body (json) returns 400" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/CommunicationParties") {
                    contentType(Json)
                    setBody("{ not-valid-json }")
                }

                response.status shouldBe BadRequest
            }
        }

        "POST /CommunicationParties returns 500 on unexpected exception" {
            val ediClient = fakeEdiClient { throw RuntimeException("boom") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/CommunicationParties") {
                    contentType(Json)
                    setBody(
                        """{
                            "businessDocument":  ${base64EncodedDocument()},
                            "contentType": "application/xml",
                            "contentTransferEncoding": "base64"
                        }"""
                    )
                }

                response.status shouldBe InternalServerError
            }
        }

        "GET /CommunicationParties returns EDI response with authentication" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/CommunicationParties?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/CommunicationParties?receiverHerIds=1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
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
