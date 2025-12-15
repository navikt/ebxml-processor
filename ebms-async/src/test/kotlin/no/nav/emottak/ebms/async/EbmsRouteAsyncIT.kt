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
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.KafkaTestContainer
import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.async.util.EventRegistrationServiceFake
import no.nav.emottak.message.model.AsyncPayload
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.uuid.Uuid
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class EbmsRouteAsyncIT {

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

    @Test
    fun `Payload endpoint returns list of payloads`() = validationTestApp {
        val validReferenceId = Uuid.random()
        val validAuthToken = getToken().serialize()

        // Testdata:
        payloadRepository.updateOrInsert(
            AsyncPayload(
                validReferenceId,
                "attachment-0fa6e663-010a-4764-85b4-94081119497a@eik.no",
                "application/pkcs7-mime",
                "Payload test content 1".toByteArray()
            )
        )
        payloadRepository.updateOrInsert(
            AsyncPayload(
                validReferenceId,
                "attachment-c53f9027-ffa4-4770-95f4-8ed0463b87c3@eik.no",
                "application/pkcs7-mime",
                "Payload test content 2".toByteArray()
            )
        )

        val httpResponse: HttpResponse = getRequestWithAuth("/api/payloads/$validReferenceId", validAuthToken)

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        val listOfPayloads = httpResponse.body<List<AsyncPayload>>()

        assertNotNull(listOfPayloads)
        assertEquals(2, listOfPayloads.size)
    }

    @Test
    fun `Payload endpoint returns 404 Not Found when no payload is found`() = validationTestApp {
        val validReferenceId = Uuid.random()
        val validAuthToken = getToken().serialize()
        val httpResponse: HttpResponse = getRequestWithAuth("/api/payloads/$validReferenceId", validAuthToken)
        assertEquals(HttpStatusCode.NotFound, httpResponse.status)
    }

    @Test
    fun `Payload endpoint returns 400 Bad Request with invalid referenceId`() = validationTestApp {
        val invalidReferenceId = "df68056e"
        val validAuthToken = getToken().serialize()
        val httpResponse: HttpResponse = getRequestWithAuth("/api/payloads/$invalidReferenceId", validAuthToken)
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)
    }

    @Test
    fun `Payload endpoint returns 401 Unauthorized with invalid autorization token`() = validationTestApp {
        val validReferenceId = "df68056e-5cba-4351-9085-c37b925b8ddd"
        val invalidAuthToken = "invalid"
        val httpResponse: HttpResponse = getRequestWithAuth("/api/payloads/$validReferenceId", invalidAuthToken)
        assertEquals(HttpStatusCode.Unauthorized, httpResponse.status)
    }

    @Test
    fun `Payload endpoint returns 401 Unauthorized without authentication token`() = validationTestApp {
        val validReferenceId = "df68056e-5cba-4351-9085-c37b925b8ddd"
        val httpResponse: HttpResponse = getHttpClient().get("/api/payloads/$validReferenceId")
        assertEquals(HttpStatusCode.Unauthorized, httpResponse.status)
    }

    private fun ApplicationTestBuilder.getHttpClient() = createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }

    private suspend fun ApplicationTestBuilder.getRequestWithAuth(urlString: String, token: String) =
        getHttpClient().get(urlString) {
            headers {
                append("Authorization", "Bearer $token")
            }
        }

    private fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )

    companion object {
        lateinit var mockOAuth2Server: MockOAuth2Server
        lateinit var payloadRepository: PayloadRepository
        private lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        private lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            println("=== Initializing Kafka Test Container ===")
            KafkaTestContainer.start()
            System.setProperty("KAFKA_BROKERS", KafkaTestContainer.bootstrapServers)
            KafkaTestContainer.createTopic(config().kafkaSignalProducer.topic)

            initDB()

            println("=== Initializing MockOAuth2Server ===")
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            KafkaTestContainer.stop()
            ebmsProviderDbContainer.stop()
            mockOAuth2Server.shutdown()
        }

        private fun initDB() {
            println("=== Initializing Payload DB Test Container ===")
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            payloadRepository = PayloadRepository(ebmsProviderDb)
        }
    }
}
