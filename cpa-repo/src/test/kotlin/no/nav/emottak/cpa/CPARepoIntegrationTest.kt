package no.nav.emottak.cpa

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.auth.AZURE_AD_AUTH
import no.nav.emottak.cpa.auth.AuthConfig
import no.nav.emottak.cpa.databasetest.PostgresOracleTest
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Direction.IN
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Party
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.SignatureDetailsRequest
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPARepoIntegrationTest : PostgresOracleTest() {

    private fun <T> cpaRepoTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application(cpaApplicationModule(postgres.dataSource, postgres.dataSource, oracle.dataSource))
        testBlock()
    }

    private val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }

    @Test
    fun `Hent sertifikat for signatursjekk`() = cpaRepoTestApp {
        val request = SignatureDetailsRequest(
            cpaId = "nav:qass:35065", // TODO endres hvis/når respons fra getCpa ikke lenger er hardkodet
            partyType = "HER",
            partyId = "8141253",
            role = "Behandler",
            service = "BehandlerKrav",
            action = "OppgjorsMelding"
        )
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = httpClient.post("/signing/certificate") {
            setBody(request)
            contentType(Json)
        }
        val body = response.body<SignatureDetails>()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", body.signatureAlgorithm)
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256", body.hashFunction)
    }

    @Test
    fun `Test egenandelfritak partyTo PartyFrom resolve`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val validationRequest = ValidationRequest(
            IN,
            "e17eb03e-9e43-43fb-874c-1fde9a28c308",
            "1234",
            "nav:qass:31162",
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "HarBorgerEgenandelFritak",
                "EgenandelForesporsel"
            )
        )
        val response = httpClient.post("/cpa/validate/121212") {
            setBody(validationRequest)
            contentType(Json)
        }

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertEquals(1, validationResult.signalEmailAddress.size)
        assertEquals("mailto://TEST_A1_Haugerud_t1@edi.nhn.no", validationResult.signalEmailAddress.first().emailAddress)
        assertTrue(validationResult.receiverEmailAddress.isEmpty()) // Channel-protokoll er HTTP
    }

    @Test
    fun `Bytt from og to - role service action matcher ikke`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val validationRequest = ValidationRequest(
            IN,
            "e17eb03e-9e43-43fb-874c-1fde9a28c308",
            "1234",
            "nav:qass:31162",
            Addressing(
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
                "HarBorgerEgenandelFritak",
                "EgenandelForesporsel"
            )
        )
        val response = httpClient.post("/cpa/validate/121212") {
            setBody(validationRequest)
            contentType(Json)
        }

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertNotNull(validationResult.error)
        assertEquals(validationResult.error?.size, 1)
        assertEquals(validationResult.error?.first()?.code, ErrorCode.DELIVERY_FAILURE)
        assertEquals(validationResult.error?.first()?.descriptionText, "Action EgenandelForesporsel matcher ikke service HarBorgerEgenandelFritak for sending party NAV")
    }

    @Test
    fun `Test hente epost`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val validationRequest = ValidationRequest(
            IN,
            "e17eb03e-9e43-43fb-874c-1fde9a28c308",
            "1234",
            "nav:qass:31162",
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "OppgjorsKontroll",
                "Oppgjorskrav"
            )
        )
        val response = httpClient.post("/cpa/validate/121212") {
            setBody(validationRequest)
            contentType(Json)
        }

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertEquals(1, validationResult.signalEmailAddress.size)
        assertEquals("mailto://TEST_A1_Haugerud_t1@edi.nhn.no", validationResult.signalEmailAddress.first().emailAddress)
        assertEquals(1, validationResult.receiverEmailAddress.size)
        assertEquals("mailto://mottak-qass@test-es.nav.no", validationResult.receiverEmailAddress.first().emailAddress)
    }

    @Test
    fun `Test hente epost - flere ChannelId'er og flere endpoints (epostadresser)`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val validationRequest = ValidationRequest(
            IN,
            "e17eb03e-9e43-43fb-874c-1fde9a28c308",
            "1234",
            "multiple_channels_and_multiple_endpoints",
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "OppgjorsKontroll",
                "Oppgjorskrav"
            )
        )
        val response = httpClient.post("/cpa/validate/121212") {
            setBody(validationRequest)
            contentType(Json)
        }

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertEquals(2, validationResult.signalEmailAddress.size)
        assertContains(
            validationResult.signalEmailAddress,
            EmailAddress("mailto://allPurpose@edi.nhn.no", EndpointTypeType.ALL_PURPOSE),
            "'signalEmailAddress' inneholdt IKKE allPurpose-epostadresse"
        )
        assertContains(
            validationResult.signalEmailAddress,
            EmailAddress("mailto://error@edi.nhn.no", EndpointTypeType.ERROR),
            "'signalEmailAddress' inneholdt IKKE error-epostadresse"
        )

        assertEquals(5, validationResult.receiverEmailAddress.size)
        assertContains(
            validationResult.receiverEmailAddress,
            EmailAddress("mailto://mottak-qass@test-es.nav.no", EndpointTypeType.ALL_PURPOSE),
            "'receiverEmailAddress' inneholdt IKKE allPurpose-epostadresse"
        )
        assertContains(
            validationResult.receiverEmailAddress,
            EmailAddress("mailto://request@test-es.nav.no", EndpointTypeType.REQUEST),
            "'receiverEmailAddress' inneholdt IKKE request-epostadresse"
        )
        assertContains(
            validationResult.receiverEmailAddress,
            EmailAddress("mailto://response@test-es.nav.no", EndpointTypeType.RESPONSE),
            "'receiverEmailAddress' inneholdt IKKE response-epostadresse"
        )
        assertContains(
            validationResult.receiverEmailAddress,
            EmailAddress("mailto://error@test-es.nav.no", EndpointTypeType.ERROR),
            "'receiverEmailAddress' inneholdt IKKE error-epostadresse"
        )
        assertContains(
            validationResult.receiverEmailAddress,
            EmailAddress("mailto://login@test-es.nav.no", EndpointTypeType.LOGIN),
            "'receiverEmailAddress' inneholdt IKKE login-epostadresse"
        )
    }

    @Test
    fun `Hent cpaId map`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = httpClient.get("/cpa/timestamps") {
            headers {
                header("cpaIds", "nav:qass:35065")
            }
        }
        println("RESPONSE WAS: " + response.bodyAsText())

        // ingen header gir alle verdier
        val responseMedAlle = httpClient.get("/cpa/timestamps")
        assertContains(responseMedAlle.bodyAsText(), "nav:qass:35065")
    }

    @Test
    fun `Upsert true på CPA`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = httpClient.get("/cpa/timestamps") {
            headers {
                header("cpaIds", "nav:qass:35065")
            }
        }
        println("RESPONSE WAS: " + response.bodyAsText())

        // ingen header gir alle verdier
        val responseMedAlle = httpClient.get("/cpa/timestamps")
        assertContains(responseMedAlle.bodyAsText(), "nav:qass:35065")
    }

    // @Test // TODO fix
    fun `Henter latest timestamp`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val updatedTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        // Putter CPA

        httpClient.post("/cpa") {
            headers {
                header("updated_date", updatedTimestamp)
                header("upsert", true)
            }
            setBody(
                xmlMarshaller.marshal(loadTestCPA("nav-qass-35065.xml"))
            )
        }
        // ingen header gir alle verdier
        val responseMedAlle = httpClient.get("/cpa/timestamps/latest")

        assertEquals(updatedTimestamp.toString(), responseMedAlle.bodyAsText())
    }

    @Test
    fun `Henter timestamps map`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val updatedTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        // Putter CPA
        httpClient.post("/cpa") {
            headers {
                header("updated_date", updatedTimestamp)
                header("upsert", true)
            }
            setBody(
                xmlMarshaller.marshal(loadTestCPA("nav-qass-35065.xml"))
            )
        }
        val responseMedAlle = httpClient.get("/cpa/timestamps")
            .body<Map<String, String>>()
        assertNotNull(responseMedAlle)
    }

    @Test
    fun `Get cpa with ID should return CPA`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = httpClient.get("/cpa/nav:qass:35065")
        assertTrue(
            StringUtils.isNotBlank(response.bodyAsText()),
            "Response can't be null or blank"
        )
    }

    @Test
    fun `Should require valid token`() = cpaRepoTestApp {
        val token = mockOAuth2Server
            .issueToken(
                AZURE_AD_AUTH,
                "testUser"
            )
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = httpClient.get("/whoami") {
            header(
                "Authorization",
                "Bearer " +
                    token.serialize()
            )
        }
        assertTrue(response.bodyAsText().contains("Gyldig"))
    }

    // @Test
    fun `Delete CPA without token is rejected`() = cpaRepoTestApp {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.delete("/cpa/delete/nav:qass:35065")
        assertNotEquals("nav:qass:35065 slettet!", response.bodyAsText())
    }

    @Test
    fun `Delete CPA should result in deletion`() = cpaRepoTestApp {
        val c = createClient {
            install(ContentNegotiation) {
                json()
            }
            installCpaRepoAuthentication()
        }
        val response = c.delete("/cpa/delete/nav:qass:35065")
        assertEquals("nav:qass:35065 slettet!", response.bodyAsText())
    }

    suspend fun getCpaRepoToken(): BearerTokens {
        val requestBody =
            "client_id=" + getEnvVar("AZURE_APP_CLIENT_ID", "cpa-repo") +
                "&client_secret=" + getEnvVar("AZURE_APP_CLIENT_SECRET", "dummysecret") +
                "&scope=" + AuthConfig.getScope() +
                "&grant_type=client_credentials"

        return HttpClient(CIO).post(
            getEnvVar(
                "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT",
                "http://localhost:3344/$AZURE_AD_AUTH/token"
            )
        ) {
            headers {
                header("Content-Type", "application/x-www-form-urlencoded")
            }
            setBody(requestBody)
        }.bodyAsText()
            .let { tokenResponseString ->
                SignedJWT.parse(
                    LENIENT_JSON_PARSER.decodeFromString<Map<String, String>>(tokenResponseString)["access_token"] as String
                )
            }
            .let { parsedJwt ->
                BearerTokens(
                    parsedJwt.serialize(),
                    "dummy"
                ) // FIXME dumt at den ikke tillater null for refresh token. Tyder på at den ikke bør brukes. Kanskje best å skrive egen handler
            }
    }

    fun HttpClientConfig<*>.installCpaRepoAuthentication() {
        install(Auth) {
            bearer {
                refreshTokens { // FIXME dumt at pluginen refresher token på 401 og har ingen forhold til expires-in
                    getCpaRepoToken()
                }
            }
        }
    }

    val LENIENT_JSON_PARSER = Json {
        isLenient = true
    }

    private fun loadTestCPA(cpaName: String): CollaborationProtocolAgreement {
        val testCpaString = String(this::class.java.classLoader.getResource("cpa/$cpaName").readBytes())
        return xmlMarshaller.unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
    }
}
