package no.nav.emottak.ebms.async

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.core.toByteArray
import io.mockk.every
import io.mockk.mockk
import no.nav.emottak.ebms.async.kafka.KafkaTestContainer
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.message.ebxml.acknowledgment
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.model.AsyncPayload
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.xmlsoap.schemas.soap.envelope.Envelope
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class EbmsRouteAsyncIT {

    val payloadRepository = mockk<PayloadRepository>()
    val eventRegistrationService = EventRegistrationServiceFake()

    fun <T> validationTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application {
            install(ServerContentNegotiation) {
                json()
            }

            install(Authentication) {
                tokenValidationSupport(AZURE_AD_AUTH, AuthConfig.getTokenSupportConfig())
            }

            routing {
                authenticate(AZURE_AD_AUTH) {
                    getPayloads(payloadRepository, eventRegistrationService)
                }
            }
        }
        testBlock()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Payload endpoint returns list of payloads`() = validationTestApp {
        val validReferenceId = Uuid.random()
        val validAuthToken = getToken().serialize()
        val mockedListOfPayloads = listOf(
            AsyncPayload(
                validReferenceId.toString(),
                "attachment-0fa6e663-010a-4764-85b4-94081119497a@eik.no",
                "application/pkcs7-mime",
                "Payload test content 1".toByteArray()
            ),
            AsyncPayload(
                validReferenceId.toString(),
                "attachment-c53f9027-ffa4-4770-95f4-8ed0463b87c3@eik.no",
                "application/pkcs7-mime",
                "Payload test content 2".toByteArray()
            )
        )

        every { payloadRepository.getByReferenceId(validReferenceId) } returns mockedListOfPayloads

        val httpClient = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/api/payloads/$validReferenceId") {
            headers {
                append("Authorization", "Bearer $validAuthToken")
            }
        }

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        val listOfPayloads = httpResponse.body<List<AsyncPayload>>()

        assertNotNull(listOfPayloads)
        assertEquals(2, listOfPayloads.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `Payload endpoint returns 404 Not Found when no payload is found`() = validationTestApp {
        val validReferenceId = Uuid.random()
        val validAuthToken = getToken().serialize()

        // As if no payload found for this reference ID
        every { payloadRepository.getByReferenceId(validReferenceId) } returns listOf()

        val httpClient = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/api/payloads/$validReferenceId") {
            headers {
                append("Authorization", "Bearer $validAuthToken")
            }
        }

        assertEquals(HttpStatusCode.NotFound, httpResponse.status)
    }

    @Test
    fun `Payload endpoint returns 400 Bad Request with invalid referenceId`() = validationTestApp {
        val invalidReferenceId = "df68056e"
        val validAuthToken = getToken().serialize()

        val httpClient = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/api/payloads/$invalidReferenceId") {
            headers {
                append("Authorization", "Bearer $validAuthToken")
            }
        }

        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)
    }

    @Test
    fun `Payload endpoint returns 401 Unauthorized without authentication`() = validationTestApp {
        val validReferenceId = "df68056e-5cba-4351-9085-c37b925b8ddd"
        val invalidAuthToken = "invalid"

        val httpClient = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/api/payloads/$validReferenceId") {
            headers {
                append("Authorization", "Bearer $invalidAuthToken")
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, httpResponse.status)
    }

    fun Envelope.assertAcknowledgmen() {
        assertNotNull(this.header!!.messageHeader())
        assertNotNull(this.header!!.acknowledgment())
    }

    protected fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server!!.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )

    companion object {
        protected var mockOAuth2Server: MockOAuth2Server? = null

        @JvmStatic
        @BeforeAll
        fun setup() {
            println("=== Kafka Test Container ===")
            KafkaTestContainer.start()
            System.setProperty("KAFKA_BROKERS", KafkaTestContainer.bootstrapServers)

            KafkaTestContainer.createTopic(config().kafkaSignalProducer.topic)

            println("=== Initializing MockOAuth2Server ===")
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            KafkaTestContainer.stop()
            mockOAuth2Server?.shutdown()
        }
    }
}
