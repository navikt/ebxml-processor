package no.nav.emottak.ediadapter.server.plugin

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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
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
import no.nav.emottak.ediadapter.server.auth.AuthConfig.Companion.getTokenSupportConfig
import no.nav.emottak.ediadapter.server.config
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

private val log = KotlinLogging.logger {}

private const val MESSAGE1 = "https://example.com/messages/1"

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

        val invalidAudience = "api://dev-fss.helsemelding.some-other-service/.default"

        beforeSpec {
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        }

        "GET /messages with single receiver her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1")

                response.status shouldBe OK

                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with multiple receiver her ids returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&ReceiverHerIds=2"
                respond("""[{"id":"100", "receiverHerId": "1"}, {"id":"200", "receiverHerId": "2"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&receiverHerIds=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}, {"id":"200", "receiverHerId": "2"}]"""
            }
        }

        "GET /messages with receiver her id and sender her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&SenderHerId=2"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&senderHerId=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and business document id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&BusinessDocumentId=10"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&businessDocumentId=10")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and messages to fetch returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&MessagesToFetch=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&messagesToFetch=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and messages to fetch (0) returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&messagesToFetch=0")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldBe "Messages to fetch must be a number between 1 and 100"
            }
        }

        "GET /messages with receiver her id and messages to fetch (101) returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&messagesToFetch=101")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldBe "Messages to fetch must be a number between 1 and 100"
            }
        }

        "GET /messages with receiver her id and order by ASC returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&OrderBy=1"
                respond("""[{"id":"100", "receiverHerId": "1"}, {"id":"101", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&orderBy=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}, {"id":"101", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and order by DESC returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&OrderBy=2"
                respond("""[{"id":"101", "receiverHerId": "1"}, {"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&orderBy=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"101", "receiverHerId": "1"}, {"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and order by non valid sorting returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&orderBy=3")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldBe "Order by must be 1 (Ascending) or 2 (Descending)"
            }
        }

        "GET /messages with receiver her id and include metadata returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&IncludeMetadata=true"
                respond(
                    """{
                    "id": "100",
                    "contentType": "application/xml",
                    "receiverHerId": 1,
                    "senderHerId": 2,
                    "businessDocumentId": "10",
                    "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                    "isAppRec": false,
                    "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                }"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&includeMetadata=true")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{
                    "id": "100",
                    "contentType": "application/xml",
                    "receiverHerId": 1,
                    "senderHerId": 2,
                    "businessDocumentId": "10",
                    "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                    "isAppRec": false,
                    "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                }"""
            }
        }

        "GET /messages with receiver her id and non valid include metadata returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&includeMetadata=foobar")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldBe "Include metadata must be 'true' or 'false'"
            }
        }

        "GET /messages with blank receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=")
                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages without receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages/{id} returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/42"
                respond(
                    """{
                     "id": "42",
                     "contentType": "application/xml",
                     "receiverHerId": 1,
                     "senderHerId": 2,
                     "businessDocumentId": "100",
                     "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                     "isAppRec": false,
                     "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                    }"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/42")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{
                     "id": "42",
                     "contentType": "application/xml",
                     "receiverHerId": 1,
                     "senderHerId": 2,
                     "businessDocumentId": "100",
                     "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                     "isAppRec": false,
                     "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                    }"""
            }
        }

        "GET /messages/{id} with blank message id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/%20")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Message id"
            }
        }

        "GET /messages/{id} missing message id returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/")

                response.status shouldBe NotFound
            }
        }

        "GET /messages/{id}/document returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/99/business-document"
                respond("<xml>doc</xml>")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/99/document")

                response.status shouldBe OK
                response.bodyAsText() shouldBe "<xml>doc</xml>"
            }
        }

        "GET /messages/{id}/status returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/55/status"
                respond(
                    """[
                         {
                           "receiverHerId": 1,
                           "transportDeliveryState": "Acknowledged",
                           "sent": true,
                           "appRecStatus": null
                         }
                       ]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/55/status")

                response.status shouldBe OK
                response.bodyAsText() shouldBe
                    """[
                         {
                           "receiverHerId": 1,
                           "transportDeliveryState": "Acknowledged",
                           "sent": true,
                           "appRecStatus": null
                         }
                       ]"""
            }
        }

        "GET /messages/{id}/apprec returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/10/apprec"
                respond(
                    """[
                        { "receiverHerId": 1,
                          "appRecStatus": Ok,
                          "appRecErrorList": null
                        }
                      ]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/10/apprec")

                response.status shouldBe OK
                response.bodyAsText() shouldBe
                    """[
                        { "receiverHerId": 1,
                          "appRecStatus": Ok,
                          "appRecErrorList": null
                        }
                      ]"""
            }
        }

        "POST /messages with empty body returns 415" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody("")
                }

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /messages without body returns 415" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/messages")

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /messages with invalid body (json) returns 400" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody("{ not-valid-json }")
                }

                response.status shouldBe BadRequest
            }
        }

        "POST /messages returns 500 on unexpected exception" {
            val ediClient = fakeEdiClient { throw RuntimeException("boom") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages") {
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

        "POST /messages/{id}/apprec/{sender} with blank sender returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages/77/apprec/%20") {
                    contentType(Json)
                    setBody("""{ "appRecStatus":"1", "appRecErrorList":[] }""")
                }

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Sender"
            }
        }

        "POST /messages/{id}/apprec missing sender returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages/77/apprec/") {
                    contentType(Json)
                    setBody("""{"status":"1"}""")
                }
                response.status shouldBe NotFound
            }
        }

        "PUT /messages/{id}/read/{herId} marks message as read" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/5/read/111"
                respond("", status = NoContent)
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/111")

                response.status shouldBe NoContent
            }
        }

        "PUT /messages/{id}/read/{herId} with blank herId returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/%20")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Her id"
            }
        }

        "PUT /messages/{id}/read missing herId returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/")

                response.status shouldBe NotFound
            }
        }

        "GET /messages returns EDI response with authentication" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/messages?receiverHerIds=1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages returns Unauthorised if access token is missing" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.get("/api/v1/messages?id=1")

                response.status shouldBe Unauthorized
            }
        }

        "GET /messages returns Unauthorised if access token is invalid" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/messages?id=1", getToken, invalidAudience)

                response.status shouldBe Unauthorized
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
