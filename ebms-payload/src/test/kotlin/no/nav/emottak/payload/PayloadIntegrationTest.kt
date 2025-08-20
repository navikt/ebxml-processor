@file:OptIn(ExperimentalUuidApi::class)

package no.nav.emottak.payload

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.PayloadResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.uuid.ExperimentalUuidApi

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayloadIntegrationTest : PayloadTestBase() {

    @AfterAll
    fun tearDown() = mockOAuth2Server.shutdown()

    @Test
    fun `Payload endepunkt med auth token gir 200 OK`() = testApp {
        client(authenticated = true).post("/payload") {
            setBody(baseRequest())
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertNull(body<PayloadResponse>().error)
        }
    }

    @Test
    fun `Payload endepunkt uten auth token gir 401 Unauthorized`() = testApp {
        client(authenticated = false).post("/payload") {
            setBody(baseRequest())
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `Payload endepunkt uten riktig audience gir 401 Unauthorized`() = testApp {
        client(audience = "wrong").post("/payload") {
            setBody(baseRequest())
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `Payload endepunkt uten audience gir 401 Unauthorized`() = testApp {
        client(audience = null).post("/payload") {
            setBody(baseRequest())
        }.let { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `Payload endepunkt med prosesseringsfeil gir 400 Bad Request og error melding`() = testApp {
        client(authenticated = true).post("/payload") {
            setBody(baseRequest().withEncryption())
        }.let { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ErrorCode.UNKNOWN, response.body<PayloadResponse>().error!!.code)
            assertEquals("Feil ved dekryptering", response.body<PayloadResponse>().error!!.descriptionText)
        }
    }

    // @Test TODO fixme
    fun `Payload endepunkt med HelseID`() = testApp {
        val expectedNationalIdentityNumber = "25027600363"
        val requestBody = baseRequest(payload = Fixtures.validEgenandelForesporselHelseId()).withOCSP()

        val response = client(authenticated = true).post("/payload") {
            setBody(requestBody)
        }

        with(response.body<PayloadResponse>()) {
            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(error)
            assertEquals(expectedNationalIdentityNumber, processedPayload!!.signedBy)
        }
    }

    @Test
    fun `Payload endepunkt med OCSP`() = testApp {
        val ssn = "01010112345"

        val requestBody = baseRequest().withOCSP()
        val httpResponse = client(authenticated = true).post("/payload") {
            header(
                "Authorization",
                "Bearer ${getToken().serialize()}"
            )
            setBody(requestBody)
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertNull(httpResponse.body<PayloadResponse>().error)
        assertEquals(ssn, httpResponse.body<PayloadResponse>().processedPayload!!.signedBy)
    }

    private fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )
}
