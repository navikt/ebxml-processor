@file:OptIn(ExperimentalUuidApi::class)

package no.nav.emottak.payload

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockkConstructor
import io.mockk.slot
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.ssnPolicyID
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.util.createDocument
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
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
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val testKeystore = KeyStoreManager(*payloadSigneringConfig().toTypedArray())

abstract class PayloadTestBase {

    protected val mockOAuth2Server = MockOAuth2Server().apply { start(port = 3344) }
    protected val validCert = testKeystore.getCertificate("samhandler-2024").encoded

    protected fun ApplicationTestBuilder.client(
        audience: String? = null,
        authenticated: Boolean = false
    ): HttpClient = createClient {
        install(ContentNegotiation) { json() }
        defaultRequest {
            when {
                audience != null -> header("Authorization", "Bearer ${token(audience).serialize()}")
                authenticated -> header("Authorization", "Bearer ${token().serialize()}")
            }
            contentType(ContentType.Application.Json)
        }
    }

    protected fun token(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )

    protected fun <T> testApp(testBlock: suspend ApplicationTestBuilder.() -> T) =
        testApplication {
            setupEnv()

            configureOcspStatusService()

            val eventRegistrationService = EventRegistrationServiceFake()
            val processor = Processor(eventRegistrationService)

            application(payloadApplicationModule(processor, eventRegistrationService))
            testBlock()
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

    protected fun baseRequest(
        messageId: String = "123",
        conversationId: String = "321",
        addressing: Addressing = Fixtures.addressing(),
        processing: PayloadProcessing = Fixtures.processing(validCert),
        payload: Payload = Fixtures.validEgenandelForesporsel()
    ): PayloadRequest = PayloadRequest(
        direction = Direction.IN,
        messageId = messageId,
        conversationId = conversationId,
        addressing = addressing,
        requestId = Uuid.random().toString(),
        processing = processing,
        payload = payload
    )

    protected object Fixtures {

        fun addressing() = Addressing(
            to = Party(listOf(PartyId("HERID", "NAVS-herid")), "NAV"),
            from = Party(listOf(PartyId("HERID", "SamhandlersHerid")), "EksternSamhandler"),
            service = "Service",
            action = "action"
        )

        fun processing(cert: ByteArray) = PayloadProcessing(
            signingCertificate = signatureDetailsWithCertResource(cert),
            encryptionCertificate = byteArrayOf(),
            processConfig = ProcessConfig(
                kryptering = false,
                komprimering = false,
                signering = true,
                internformat = false,
                validering = false,
                apprec = false,
                ocspSjekk = false,
                juridiskLogg = false,
                adapter = null,
                errorAction = null
            )
        )

        private fun signedPayload(resource: String) = Payload(
            bytes = PayloadSignering().signerXML(
                createDocument(
                    object {}::class.java.classLoader.getResource(resource)!!.openStream()
                ),
                signatureDetails()
            ).asByteArray(),
            contentType = ""
        )

        fun validEgenandelForesporsel() = signedPayload("xml/egenandelforesporsel.xml")
        fun validEgenandelForesporselHelseId() = signedPayload("helseid/xml/egenandelforesporsel-helseid-ok.xml")
    }

    protected fun PayloadRequest.withOCSP() = copy(
        processing = processing.copy(
            processConfig = processing.processConfig.copy(ocspSjekk = true)
        )
    )

    protected fun PayloadRequest.withEncryption() = copy(
        processing = processing.copy(
            processConfig = processing.processConfig.copy(kryptering = true)
        )
    )
}

private fun signatureDetailsWithCertResource(certificate: ByteArray) = SignatureDetails(
    certificate = certificate,
    signatureAlgorithm = "sha256WithRSAEncryption",
    hashFunction = ""
)

private fun signatureDetails() = SignatureDetails(
    certificate = testKeystore.getCertificate("samhandler-2024").encoded,
    signatureAlgorithm = "sha256WithRSAEncryption",
    hashFunction = ""
)
