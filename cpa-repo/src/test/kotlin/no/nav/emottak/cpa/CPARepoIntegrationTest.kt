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
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.auth.AZURE_AD_AUTH
import no.nav.emottak.cpa.auth.AuthConfig
import no.nav.emottak.cpa.persistence.DBTest
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.melding.model.SignatureDetailsRequest
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.util.getEnvVar
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPARepoIntegrationTest : DBTest() {

    fun <T> cpaRepoTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application(cpaApplicationModule(db.dataSource, db.dataSource))
        testBlock()
    }

    val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }

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
            contentType(ContentType.Application.Json)
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
            contentType(ContentType.Application.Json)
        }

        println(String(response.readBytes()))
    }

    @Test
    fun `Byt from og to - role service action matcher ikke`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val validationRequest = ValidationRequest(
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
            contentType(ContentType.Application.Json)
        }

        println(String(response.readBytes()))
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
                BearerTokens(parsedJwt.serialize(), "dummy") // FIXME dumt at den ikke tillater null for refresh token. Tyder på at den ikke bør brukes. Kanskje best å skrive egen handler
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
}
