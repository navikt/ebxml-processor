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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.cpa.auth.AZURE_AD_AUTH
import no.nav.emottak.cpa.auth.AuthConfig
import no.nav.emottak.cpa.databasetest.PostgresOracleTest
import no.nav.emottak.cpa.persistence.CPARepository
import no.nav.emottak.cpa.persistence.gammel.PartnerRepository
import no.nav.emottak.cpa.util.EventRegistrationServiceFake
import no.nav.emottak.message.model.Direction.IN
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.MessagingCharacteristicsRequest
import no.nav.emottak.message.model.MessagingCharacteristicsResponse
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.SignatureDetailsRequest
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.OSLO_ZONE
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.utils.environment.getEnvVar
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.PerMessageCharacteristicsType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPARepoIntegrationTest : PostgresOracleTest() {
    val eventRegistrationService = EventRegistrationServiceFake()
    private lateinit var cpaRepositoryMock: CPARepository
    private lateinit var partnerRepositoryMock: PartnerRepository

    private fun <T> cpaRepoTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application(
            cpaApplicationModule(
                postgres.dataSource,
                postgres.dataSource,
                oracle.dataSource,
                eventRegistrationService
            )
        )
        testBlock()
    }

    private fun <T> validateCpaMockTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        clearAllMocks()
        cpaRepositoryMock = mockk()
        partnerRepositoryMock = mockk(relaxed = true)
        application(validateCpaApplicationModule(cpaRepositoryMock, partnerRepositoryMock))
        testBlock()
    }

    private fun validateCpaApplicationModule(
        cpaRepository: CPARepository,
        partnerRepository: PartnerRepository
    ): Application.() -> Unit {
        return {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
            routing {
                validateCpa(cpaRepository, partnerRepository, eventRegistrationService)
            }
        }
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
        val response = runValidateCpa(
            httpClient,
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "HarBorgerEgenandelFritak",
                "EgenandelForesporsel"
            )
        )

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertEquals(1, validationResult.signalEmailAddress.size)
        assertEquals("mailto://mottak-qass@test-es.nav.no", validationResult.signalEmailAddress.first().emailAddress)
        assertTrue(validationResult.receiverEmailAddress.isEmpty()) // Channel-protokoll er HTTP
    }

    @Test
    fun `Bytt from og to - role service action matcher ikke`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = runValidateCpa(
            httpClient,
            Addressing(
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                Party(listOf(PartyId("HER", "79768")), "Frikortregister"),
                "HarBorgerEgenandelFritak",
                "EgenandelForesporsel"
            )
        )

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertNotNull(validationResult.error)
        assertEquals(1, validationResult.error?.size)
        assertEquals(ErrorCode.DELIVERY_FAILURE, validationResult.error?.first()?.code)
        assertEquals(
            "Action EgenandelForesporsel matcher ikke service HarBorgerEgenandelFritak for sending party NAV",
            validationResult.error?.first()?.descriptionText,
            "Forventet feilmelding matcher ikke"
        )
    }

    @Test
    fun `Test hente epost`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = runValidateCpa(
            httpClient,
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "OppgjorsKontroll",
                "Oppgjorskrav"
            )
        )

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        assertEquals(1, validationResult.signalEmailAddress.size)
        assertEquals("mailto://mottak-qass@test-es.nav.no", validationResult.signalEmailAddress.first().emailAddress)
        assertEquals(1, validationResult.receiverEmailAddress.size)
        assertEquals("mailto://mottak-qass@test-es.nav.no", validationResult.receiverEmailAddress.first().emailAddress)
        assertEquals(1, validationResult.senderEmailAddress.size)
        assertEquals("mailto://TEST_A1_Haugerud_t1@edi.nhn.no", validationResult.senderEmailAddress.first().emailAddress)
    }

    @Test
    fun `Test hente epost - flere ChannelId'er og flere endpoints (epostadresser)`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = runValidateCpa(
            httpClient,
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "OppgjorsKontroll",
                "Oppgjorskrav"
            ),
            "multiple_channels_and_multiple_endpoints"
        )

        val validationResult = response.body<ValidationResult>()
        assertNotNull(validationResult)

        with(validationResult.signalEmailAddress) {
            assertEquals(4, this.size)
            assertContains(
                this,
                EmailAddress("mailto://request@test-es.nav.no", EndpointTypeType.REQUEST),
                "'signalEmailAddress' inneholdt IKKE request-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://response@test-es.nav.no", EndpointTypeType.RESPONSE),
                "'signalEmailAddress' inneholdt IKKE response-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://error@test-es.nav.no", EndpointTypeType.ERROR),
                "'signalEmailAddress' inneholdt IKKE error-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://login@test-es.nav.no", EndpointTypeType.LOGIN),
                "'signalEmailAddress' inneholdt IKKE login-epostadresse"
            )
        }

        with(validationResult.receiverEmailAddress) {
            assertEquals(5, this.size)
            assertContains(
                this,
                EmailAddress("mailto://mottak-qass@test-es.nav.no", EndpointTypeType.ALL_PURPOSE),
                "'receiverEmailAddress' inneholdt IKKE allPurpose-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://request@test-es.nav.no", EndpointTypeType.REQUEST),
                "'receiverEmailAddress' inneholdt IKKE request-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://response@test-es.nav.no", EndpointTypeType.RESPONSE),
                "'receiverEmailAddress' inneholdt IKKE response-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://error@test-es.nav.no", EndpointTypeType.ERROR),
                "'receiverEmailAddress' inneholdt IKKE error-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://login@test-es.nav.no", EndpointTypeType.LOGIN),
                "'receiverEmailAddress' inneholdt IKKE login-epostadresse"
            )
        }

        with(validationResult.senderEmailAddress) {
            assertEquals(2, this.size)
            assertContains(
                this,
                EmailAddress("mailto://allPurpose@edi.nhn.no", EndpointTypeType.ALL_PURPOSE),
                "'senderEmailAddress' inneholdt IKKE allPurpose-epostadresse"
            )
            assertContains(
                this,
                EmailAddress("mailto://error@edi.nhn.no", EndpointTypeType.ERROR),
                "'senderEmailAddress' inneholdt IKKE error-epostadresse"
            )
        }
    }

    @Test
    fun `Hent cpaId map`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val responseMedAlle = httpClient.get("/cpa/timestamps")
        assertEquals(HttpStatusCode.OK, responseMedAlle.status)
        val responseMapAlle = responseMedAlle.body<Map<String, String>>()
        assertEquals(3, responseMapAlle.size, "Forventer alle tre timestamps")
        assertTrue(
            responseMapAlle.containsKey("nav:qass:35065"),
            "Forventet 'nav:qass:35065' i keys: ${responseMapAlle.keys}"
        )
        assertTrue(
            responseMapAlle.containsKey("nav:qass:31162"),
            "Forventet 'nav:qass:31162' i keys: ${responseMapAlle.keys}"
        )
        assertTrue(
            responseMapAlle.containsKey("multiple_channels_and_multiple_endpoints"),
            "Forventet 'multiple_channels_and_multiple_endpoints' i keys: ${responseMapAlle.keys}"
        )
    }

    @Test
    fun `Hent cpaId map - med CPA-id i header`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val responseMedEn = httpClient.get("/cpa/timestamps") {
            headers {
                header("cpaIds", "nav:qass:35065")
            }
        }
        assertEquals(HttpStatusCode.OK, responseMedEn.status)
        val responseMap1 = responseMedEn.body<Map<String, String>>()
        assertEquals(1, responseMap1.size, "Forventer én timestamp")
        assertEquals(setOf("nav:qass:35065"), responseMap1.keys)
    }

    @Test
    fun `Post CPA uten token blir avvist`() = cpaRepoTestApp {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.post("/cpa")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `Henter latest updated timestamp (deprecated endpoint - redirected)`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
            installCpaRepoAuthentication()
        }
        val latestTimestamp = setupLatestTimestampsTest(httpClient)
        val responseMedSiste = httpClient.get("/cpa/timestamps/latest")
        assertEquals("/cpa/timestamps/last_updated/latest", responseMedSiste.request.url.encodedPath)
        assertEquals(latestTimestamp.toString(), responseMedSiste.bodyAsText())
    }

    @Test
    fun `Henter latest updated timestamp`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
            installCpaRepoAuthentication()
        }
        val latestTimestamp = setupLatestTimestampsTest(httpClient)
        val responseMedSiste = httpClient.get("/cpa/timestamps/last_updated/latest")
        assertEquals(latestTimestamp.toString(), responseMedSiste.bodyAsText())
    }

    @Test
    fun `Henter timestamps map (deprecated endpoint - redirected)`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
            installCpaRepoAuthentication()
        }
        val updatedTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        postCpaRequest(httpClient, updatedTimestamp, "nav-qass-35065.xml")

        val response = httpClient.get("/cpa/timestamps")
        assertEquals("/cpa/timestamps/last_updated", response.request.url.encodedPath)
    }

    @Test
    fun `Henter timestamps map`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
            installCpaRepoAuthentication()
        }
        val updatedTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        postCpaRequest(httpClient, updatedTimestamp, "nav-qass-35065.xml")

        val response = httpClient.get("/cpa/timestamps/last_updated")
        val responseMap = response.body<Map<String, String>>()
        assertNotNull(responseMap)
        assertEquals(
            postgresTestSetup.timestamp.toString(),
            responseMap["nav:qass:31162"],
            "CPA 31162 skal ha timestamp fra postgresTestSetup"
        )
        assertEquals(
            updatedTimestamp.toString(),
            responseMap["nav:qass:35065"],
            "CPA 35065 skal ha timestamp satt av testen"
        )
        assertEquals(
            postgresTestSetup.timestamp.toString(),
            responseMap["multiple_channels_and_multiple_endpoints"],
            "CPA multiple_channels_and_multiple_endpoints skal ha timestamp fra postgresTestSetup"
        )
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
    fun `messagingCharacteristics endpoint should return MessagingCharacteristicsResponse`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val messagingCharacteristicsRequest = MessagingCharacteristicsRequest(
            requestId = Uuid.random().toString(),
            cpaId = "nav:qass:35065",
            partyIds = listOf(PartyId("HER", "8141253")),
            role = "Behandler",
            service = "BehandlerKrav",
            action = "OppgjorsMelding"
        )

        val response = httpClient.post("/cpa/messagingCharacteristics") {
            setBody(messagingCharacteristicsRequest)
            contentType(Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val messagingCharacteristicsResponse = response.body<MessagingCharacteristicsResponse>()
        assertNotNull(messagingCharacteristicsResponse)
        assertEquals(
            messagingCharacteristicsRequest.requestId,
            messagingCharacteristicsResponse.requestId,
            "Request ID should match"
        )
        assertEquals(
            PerMessageCharacteristicsType.ALWAYS,
            messagingCharacteristicsResponse.ackRequested,
            "AckRequested should be the same as in nav-qass-35065.xml file"
        )
        assertEquals(
            PerMessageCharacteristicsType.PER_MESSAGE,
            messagingCharacteristicsResponse.ackSignatureRequested,
            "AckSignatureRequested should be the same as in nav-qass-35065.xml file"
        )
        assertEquals(
            PerMessageCharacteristicsType.PER_MESSAGE,
            messagingCharacteristicsResponse.duplicateElimination,
            "DuplicateElimination should be the same as in nav-qass-35065.xml file"
        )
    }

    @Test
    fun `messagingCharacteristics endpoint should return NotFound if CPA is not found`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val messagingCharacteristicsRequest = MessagingCharacteristicsRequest(
            requestId = Uuid.random().toString(),
            cpaId = "no:such:cpa",
            partyIds = listOf(PartyId("HER", "8141253")),
            role = "Behandler",
            service = "BehandlerKrav",
            action = "OppgjorsMelding"
        )

        val response = httpClient.post("/cpa/messagingCharacteristics") {
            setBody(messagingCharacteristicsRequest)
            contentType(Json)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
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
                "Bearer " + token.serialize()
            )
        }
        assertTrue(response.bodyAsText().contains("Gyldig"))
    }

    @Test
    fun `Delete CPA without token is rejected`() = cpaRepoTestApp {
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.delete("/cpa/delete/nav:qass:35065")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
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
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("nav:qass:35065 slettet!", response.bodyAsText())
    }

    @Test
    fun `validateCpa should update CPA's last used date`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        var lastUsedMap = getLastUsedMap(httpClient)
        assertNull(lastUsedMap["nav:qass:31162"]) // Never used

        runValidateCpa(
            httpClient,
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "OppgjorsKontroll",
                "Oppgjorskrav"
            )
        )

        lastUsedMap = getLastUsedMap(httpClient)
        val cpaLastUsed = lastUsedMap["nav:qass:31162"]
        assertNotNull(cpaLastUsed) // Now it's been used

        val today = LocalDateTime.ofInstant(Instant.now(), OSLO_ZONE)
        val cpaLastUsedTimestamp = LocalDateTime.parse(cpaLastUsed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        assertEquals(today.year, cpaLastUsedTimestamp.year)
        assertEquals(today.month, cpaLastUsedTimestamp.month)
        assertEquals(today.dayOfMonth, cpaLastUsedTimestamp.dayOfMonth)
    }

    @Test
    fun `last used should not be null after CPA update`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
            installCpaRepoAuthentication()
        }

        var response = httpClient.get("/cpa/nav:qass:31162")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            StringUtils.isNotBlank(response.bodyAsText()),
            "Response can't be null or blank"
        )

        // ValidateCpa sets the lastUsed timestamp
        runValidateCpa(
            httpClient,
            Addressing(
                Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
                Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
                "OppgjorsKontroll",
                "Oppgjorskrav"
            )
        )

        // Verify that lastUsed is set
        var lastUsedMap = getLastUsedMap(httpClient)
        val cpaLastUsed = lastUsedMap["nav:qass:31162"]
        assertNotNull(cpaLastUsed)
        val timezone = ZoneId.of("Europe/Oslo")
        val today = LocalDateTime.ofInstant(Instant.now(), timezone)
        val cpaLastUsedTimestamp1 = LocalDateTime.parse(cpaLastUsed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals(today.dayOfYear, cpaLastUsedTimestamp1.dayOfYear)

        // Update the CPA
        val updatedTimestamp = Instant.now()
        postCpaRequest(httpClient, updatedTimestamp, "nav-qass-31162.xml")

        // Verify that lastUsed is not null, and is the same timestamp as previously
        lastUsedMap = getLastUsedMap(httpClient)
        assertNotNull(lastUsedMap["nav:qass:31162"], "CPA 31162 should not be null after an update")
        val cpaLastUsedTimestamp2 = LocalDateTime.parse(cpaLastUsed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals(cpaLastUsedTimestamp1, cpaLastUsedTimestamp2)

        // Verify that we still can retrieve the CPA
        response = httpClient.get("/cpa/nav:qass:31162")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            StringUtils.isNotBlank(response.bodyAsText()),
            "Response can't be null or blank"
        )
    }

    @Test
    fun `validateCpa should only call CPARepository_updateCpaLastUsed once pr day`() = validateCpaMockTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val cpaId = "nav:qass:31162"
        val cpa = loadTestCPA("nav-qass-31162.xml")
        val timestamp = Instant.now()
        val firstResult = Pair(cpa, timestamp.minus(1, ChronoUnit.DAYS)) // Last used: Yesterday
        val secondResult = Pair(cpa, timestamp.minus(1, ChronoUnit.HOURS)) // Last used: An hour ago
        val thirdResult = Pair(cpa, timestamp.minus(2, ChronoUnit.HOURS)) // Last used: Two hours ago
        val processConfig = ProcessConfig(
            kryptering = false,
            komprimering = false,
            signering = false,
            internformat = false,
            validering = false,
            apprec = false,
            ocspSjekk = false,
            juridiskLogg = false,
            adapter = null,
            errorAction = null
        )

        coEvery { cpaRepositoryMock.findCpaAndLastUsed(cpaId) } returnsMany listOf(firstResult, secondResult, thirdResult)
        coEvery { cpaRepositoryMock.updateCpaLastUsed(cpaId) } returns true
        coEvery { partnerRepositoryMock.findPartnerId(cpaId) } returns 12345L
        coEvery { cpaRepositoryMock.getProcessConfig(any(), any(), any()) } returns processConfig

        val addressing = Addressing(
            Party(listOf(PartyId("HER", "79768")), "KontrollUtbetaler"),
            Party(listOf(PartyId("HER", "8090595")), "Utleverer"),
            "OppgjorsKontroll",
            "Oppgjorskrav"
        )

        runValidateCpa(httpClient, addressing)
        runValidateCpa(httpClient, addressing)
        runValidateCpa(httpClient, addressing)

        coVerify(exactly = 3) { cpaRepositoryMock.findCpaAndLastUsed(cpaId) }
        coVerify(exactly = 1) { cpaRepositoryMock.updateCpaLastUsed(cpaId) }
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

    private suspend fun runValidateCpa(httpClient: HttpClient, addressing: Addressing, cpaId: String = "nav:qass:31162"): HttpResponse {
        val validationRequest = ValidationRequest(
            IN,
            "e17eb03e-9e43-43fb-874c-1fde9a28c308",
            "1234",
            cpaId = cpaId,
            addressing
        )
        val validateResponse = httpClient.post("/cpa/validate/121212") {
            setBody(validationRequest)
            contentType(Json)
        }
        assertEquals(HttpStatusCode.OK, validateResponse.status, "Expected OK status for validate")
        return validateResponse
    }

    private suspend fun postCpaRequest(
        httpClient: HttpClient,
        updatedTimestamp: Instant,
        filename: String = "nav-qass-31162.xml",
        expectedStatusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponse {
        val postResponse = httpClient.post("/cpa") {
            headers {
                header("updated_date", updatedTimestamp)
                header("upsert", true)
            }
            setBody(
                xmlMarshaller.marshal(loadTestCPA(filename))
            )
        }
        assertEquals(expectedStatusCode, postResponse.status, "Expected ${expectedStatusCode.description} status for validate")
        return postResponse
    }

    private suspend fun setupLatestTimestampsTest(httpClient: HttpClient): Instant {
        // Oppdatér CPA 35065 med gårdagens dato
        val updatedTimestamp1 = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        postCpaRequest(httpClient, updatedTimestamp1, "nav-qass-35065.xml")

        // Oppdatér CPA 31162 med dato forgårs
        val updatedTimestamp2 = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        postCpaRequest(httpClient, updatedTimestamp2, "nav-qass-31162.xml")

        // Slett den siste CPA'en slik at vi kun har to CPA'er i databasen
        val deleteResponse = httpClient.delete("/cpa/delete/multiple_channels_and_multiple_endpoints")
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        return updatedTimestamp1
    }

    private suspend fun getLastUsedMap(httpClient: HttpClient): Map<String, String?> {
        val lastUsedResponse = httpClient.get("/cpa/timestamps/last_used")
        assertEquals(HttpStatusCode.OK, lastUsedResponse.status, "Expected OK status for /cpa/timestamps/last_used")
        return lastUsedResponse.body<Map<String, String?>>()
    }
}
