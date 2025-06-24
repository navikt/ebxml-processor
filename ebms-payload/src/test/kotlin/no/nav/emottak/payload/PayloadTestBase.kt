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
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.Party
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadProcessing
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.xml.asByteArray
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.util.EventRegistrationServiceFake
import no.nav.emottak.util.createDocument
import no.nav.security.mock.oauth2.MockOAuth2Server
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val testKeystore = KeyStoreManager(payloadSigneringConfig())

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

            val eventRegistrationService = EventRegistrationServiceFake()
            val processor = Processor(eventRegistrationService)

            application(payloadApplicationModule(processor, eventRegistrationService))
            testBlock()
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
