@file:OptIn(ExperimentalUuidApi::class)

package no.nav.emottak.payload

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockkConstructor
import io.mockk.slot
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.ssnPolicyID
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers.id_pkix_ocsp_nonce
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPReq
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
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
        mockkConstructor(OcspStatusService::class, recordPrivateCalls = true, localToThread = false)

        val ocspRequestCaptureSlot = slot<ByteArray>()
        coEvery {
            anyConstructed<OcspStatusService>().postOCSPRequest(any(String::class), capture(ocspRequestCaptureSlot))
        } coAnswers {
            OCSPRespBuilder().build(
                OCSPRespBuilder.SUCCESSFUL,
                createOCSPResp(
                    testKeystore.getCertificate("navtest-ca"),
                    ssn,
                    testKeystore.getKey("navtest-ca"),
                    OCSPReq(ocspRequestCaptureSlot.captured).getExtension(id_pkix_ocsp_nonce).parsedValue
                )
            )
        }

        client(authenticated = true).post("/payload") {
            setBody(baseRequest().withOCSP())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(response.body<PayloadResponse>().error)
            assertEquals(ssn, response.body<PayloadResponse>().processedPayload!!.signedBy)
        }
    }

    private fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )

    private fun configureOcspStatusService() {
        mockkConstructor(OcspStatusService::class, recordPrivateCalls = true, localToThread = false)
        val ocspRequestCaptureSlot = slot<ByteArray>()
        coEvery {
            anyConstructed<OcspStatusService>().postOCSPRequest(any(String::class), capture(ocspRequestCaptureSlot))
        } coAnswers {
            OCSPRespBuilder().build(
                OCSPRespBuilder.SUCCESSFUL,
                createOCSPResp(
                    testKeystore.getCertificate("navtest-ca"),
                    "01010112345",
                    testKeystore.getKey("navtest-ca"),
                    OCSPReq(ocspRequestCaptureSlot.captured).getExtension(id_pkix_ocsp_nonce).parsedValue
                )
            )
        }
    }
}

private fun createOCSPResp(
    responder: X509Certificate,
    ssn: String,
    pk: PrivateKey,
    asn1encodableNonce: ASN1Encodable
): BasicOCSPResp {
    val digCalcProv = JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider()).build()
    return JcaBasicOCSPRespBuilder(
        responder.issuerX500Principal
    ).addResponse(
        JcaCertificateID(
            digCalcProv.get(
                CertificateID.HASH_SHA1
            ),
            responder,
            responder.serialNumber
        ),
        CertificateStatus.GOOD
    ).setResponseExtensions(
        Extensions(
            listOf(
                Extension(
                    id_pkix_ocsp_nonce,
                    false,
                    DEROctetString(asn1encodableNonce)
                ),
                Extension(
                    ssnPolicyID,
                    true,
                    ssn.toByteArray()
                )
            ).toTypedArray()
        )
    ).build(
        JcaContentSignerBuilder(responder.sigAlgName).build(pk),
        arrayOf(X509CertificateHolder(responder.encoded)),
        Date.from(Instant.now())
    )
}
