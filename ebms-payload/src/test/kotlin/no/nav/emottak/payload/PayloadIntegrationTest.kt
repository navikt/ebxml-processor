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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.payload.apprec.message.AppRecErrorCode
import no.nav.emottak.payload.crypto.SignatureValidator
import no.nav.emottak.payload.error.SignatureException
import no.nav.emottak.payload.util.EventRegistrationService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
            assertEquals(ErrorCode.SECURITY_FAILURE, response.body<PayloadResponse>().error!!.code)
            assertEquals("Feil ved dekryptering", response.body<PayloadResponse>().error!!.descriptionText)
        }
    }

    @Test
    fun `Payload endepunkt med HelseID`() = testApp {
        val requestBody = baseRequest(payload = Fixtures.validEgenandelForesporselHelseId()).withOCSP()

        val response = client(authenticated = true).post("/payload") {
            setBody(requestBody)
        }

        with(response.body<PayloadResponse>()) {
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ErrorCode.UNKNOWN, this.error!!.code)
            assertEquals("Token does not contain required audience", this.error!!.descriptionText)
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

    @Test
    fun `Payload endepunkt med ugyldig signatur gir negativ AppRec og registrerer SIGNATURE_CHECK_FAILED en gang`() {
        val eventRegistrationService = mockk<EventRegistrationService>(relaxed = true)
        val signatureValidator = mockk<SignatureValidator>()
        every { signatureValidator.validate(any()) } throws SignatureException("Invalid Signature!")
        val request = baseRequest().let {
            it.copy(
                processing = it.processing.copy(
                    processConfig = it.processing.processConfig.copy(apprec = true)
                )
            )
        }

        testApp(eventRegistrationService, signatureValidator) {
            client(authenticated = true).post("/payload") {
                setBody(request)
            }.let { response ->
                val body = response.body<PayloadResponse>()
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals(ErrorCode.SECURITY_FAILURE, body.error!!.code)
                assertEquals("Invalid Signature!", body.error!!.descriptionText)
                assertTrue(body.apprec)
                val apprec = String(body.processedPayload!!.bytes)
                assertTrue(apprec.contains(AppRecErrorCode.S01.name), "Negative AppRec must contain error code S01")
                assertTrue(apprec.contains("Invalid Signature!"), "Negative AppRec must contain the error text")
            }
        }

        coVerify(exactly = 1) {
            eventRegistrationService.registerSignatureValidationFailed(
                match { it.requestId == request.requestId },
                isNull(inverse = true),
                ofType<SignatureException>()
            )
        }
        coVerify(exactly = 0) { eventRegistrationService.registerEvent(any(), any(), any()) }
    }

    private fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )
}
