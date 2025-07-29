@file:OptIn(ExperimentalUuidApi::class)

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
import io.mockk.coEvery
import io.mockk.mockkConstructor
import io.mockk.slot
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.ssnPolicyID
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.util.createDocument
import no.nav.security.mock.oauth2.MockOAuth2Server
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val testKeystore = KeyStoreManager(payloadSigneringConfig())

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayloadIntegrationTest {
    private val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
    private val ssn = "01010112345"

    private fun <T> ebmsPayloadTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        setupEnv()
        configureOcspStatusService()

        val eventRegistrationService = EventRegistrationServiceFake()
        val processor = Processor(eventRegistrationService)

        application(payloadApplicationModule(processor, eventRegistrationService))
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

    @Test
    fun `Payload endepunkt med OCSP`() = ebmsPayloadTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        // certificate = object {}::class.java.classLoader.getResourceAsStream("keystore/samhandlerRequest_CA-SIGNED.crt")

        val requestBody = payloadRequestMedOCSP(
            testKeystore.getCertificate("samhandler-2024").encoded
        )
        val httpResponse = httpClient.post("/payload") {
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
                    ssn,
                    testKeystore.getKey("navtest-ca"),
                    OCSPReq(ocspRequestCaptureSlot.captured).getExtension(id_pkix_ocsp_nonce).parsedValue
                )
            )
        }
    }
}

private fun createOCSPResp(responder: X509Certificate, ssn: String, pk: PrivateKey, asn1encodableNonce: ASN1Encodable): BasicOCSPResp {
    val digCalcProv = JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider()).build()
    return JcaBasicOCSPRespBuilder(
        responder.issuerX500Principal
    ).addResponse(
        JcaCertificateID(
            digCalcProv.get(
                CertificateID.HASH_SHA1
                // AlgorithmIdentifier(ASN1ObjectIdentifier(responder.sigAlgOID))
            ),
            responder,
            responder.serialNumber
        ),
        CertificateStatus.GOOD
    )
        .setResponseExtensions(
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
        )
        .build(
            JcaContentSignerBuilder(responder.sigAlgName).build(pk),
            arrayOf(X509CertificateHolder(responder.encoded)),
            Date.from(Instant.now())
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
    payload = emptyPayload(),
    addressing = addressing(),
    requestId = Uuid.random().toString()
)

private fun signatureDetailsWithCertResource(certificate: ByteArray) = SignatureDetails(
    certificate = certificate,
    signatureAlgorithm = "sha256WithRSAEncryption",
    hashFunction = ""
)

private fun payloadRequestMedOCSP(signedCert: ByteArray) = PayloadRequest(
    direction = Direction.IN,
    messageId = "123",
    conversationId = "321",
    processing = PayloadProcessing(
        signatureDetailsWithCertResource(signedCert),
        byteArrayOf(),
        ProcessConfig(
            kryptering = false,
            komprimering = false,
            signering = true,
            internformat = false,
            validering = false,
            apprec = false,
            ocspSjekk = true,
            juridiskLogg = false,
            adapter = null,
            errorAction = null
        )
    ),
    payload = dummyPayload(),
    addressing = addressing(),
    requestId = Uuid.random().toString()
)

private fun addressing() = Addressing(
    to = Party(listOf(PartyId("HERID", "NAVS-herid")), "NAV"),
    from = Party(listOf(PartyId("HERID", "SamhandlersHerid")), "EksternSamhandler"),
    service = "Service",
    action = "action"
)

private fun emptyPayload() = Payload(
    bytes = byteArrayOf(),
    contentType = "application/xml"
)

private fun dummyPayload() = Payload(
    bytes =
    PayloadSignering().signerXML(
        createDocument(
            object {}::class.java.classLoader.getResource("xml/egenandelforesporsel.xml")!!.openStream()
        ),
        signatureDetails()
    ).asByteArray(),

    contentType = ""
)

private fun payloadProcessing(
    kryptering: Boolean,
    komprimering: Boolean,
    signering: Boolean,
    internformat: Boolean
) = PayloadProcessing(
    signingCertificate = emptySignatureDetails(),
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

private fun getDummyCert() {
}

private fun signatureDetails() = SignatureDetails(
    certificate = testKeystore.getCertificate("samhandler-2024").encoded,
    signatureAlgorithm = "sha256WithRSAEncryption",
    hashFunction = ""
)

private fun emptySignatureDetails() = SignatureDetails(
    certificate = byteArrayOf(),
    signatureAlgorithm = "",
    hashFunction = ""
)
