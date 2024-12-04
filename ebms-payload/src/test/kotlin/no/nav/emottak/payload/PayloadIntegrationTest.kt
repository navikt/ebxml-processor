package no.nav.emottak.payload

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Party
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayloadIntegrationTest {

    private val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }

    private fun <T> ebmsPayloadTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        setupEnv()
        application(payloadApplicationModule())
        testBlock()
    }

    @Test
    fun `Payload endepunkt med auth token gir 200 OK`() = ebmsPayloadTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse = httpClient.post("/payload") {
            header(
                "Authorization",
                "Bearer ${getToken().serialize()}"
            )
            setBody(payloadRequest())
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertNull(httpResponse.body<PayloadResponse>().error)
    }

    @Test
    fun `Payload endepunkt uten auth token gir 401 Unauthorized`() = ebmsPayloadTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse = httpClient.post("/payload") {
            setBody(payloadRequest())
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Unauthorized, httpResponse.status)
    }

    @Test
    fun `Payload endepunkt uten riktig audience gir 401 Unauthorized`() = ebmsPayloadTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse = httpClient.post("/payload") {
            header(
                "Authorization",
                "Bearer ${getToken(audience = "other").serialize()}"
            )
            setBody(payloadRequest())
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Unauthorized, httpResponse.status)
    }

    @Test
    fun `Payload endepunkt med prosesseringsfeil gir 400 Bad Request og error melding`() = ebmsPayloadTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse = httpClient.post("/payload") {
            header(
                "Authorization",
                "Bearer ${getToken().serialize()}"
            )
            setBody(payloadRequest(kryptering = true))
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)
        assertEquals(ErrorCode.UNKNOWN, httpResponse.body<PayloadResponse>().error!!.code)
        assertEquals("Feil ved dekryptering", httpResponse.body<PayloadResponse>().error!!.descriptionText)
    }

    private fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )
}

private fun payloadRequest(
    kryptering: Boolean = false,
    komprimering: Boolean = false,
    signering: Boolean = false,
    internformat: Boolean = false
) = PayloadRequest(
    direction = Direction.IN,
    messageId = "123",
    conversationId = "321",
    processing = payloadProcessing(kryptering, komprimering, signering, internformat),
    payload = payload(),
    addressing = addressing()
)

private fun addressing() = Addressing(
    to = Party(listOf(PartyId("HERID", "NAVS-herid")), "NAV"),
    from = Party(listOf(PartyId("HERID", "SamhandlersHerid")), "EksternSamhandler"),
    service = "Service",
    action = "action"
)

private fun payload() = Payload(
    bytes = byteArrayOf(),
    contentType = "application/xml"
)

private fun payloadProcessing(
    kryptering: Boolean,
    komprimering: Boolean,
    signering: Boolean,
    internformat: Boolean
) = PayloadProcessing(
    signingCertificate = signatureDetails(),
    encryptionCertificate = byteArrayOf(),
    processConfig = processConfig(kryptering, komprimering, signering, internformat)
)

private fun processConfig(
    kryptering: Boolean,
    komprimering: Boolean,
    signering: Boolean,
    internformat: Boolean
) = ProcessConfig(
    kryptering = kryptering,
    komprimering = komprimering,
    signering = signering,
    internformat = internformat,
    validering = false,
    ocspSjekk = false,
    apprec = false,
    juridiskLogg = false,
    adapter = null,
    errorAction = null
)

private fun signatureDetails() = SignatureDetails(
    certificate = byteArrayOf(),
    signatureAlgorithm = "",
    hashFunction = ""
)
