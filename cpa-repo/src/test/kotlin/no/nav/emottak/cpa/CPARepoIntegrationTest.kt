package no.nav.emottak.cpa

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.emottak.cpa.persistence.DBTest
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.melding.model.SignatureDetailsRequest
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.ValidationRequest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPARepoIntegrationTest : DBTest() {

    fun <T> cpaRepoTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application(cpaApplicationModule(db.dataSource, db.dataSource))
        testBlock()
    }

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
    fun `Test egenandelfritak partyTo PartyFrom resolve`() = cpaRepoTestApp{
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
                Party(listOf(PartyId("HER","79768")),"Frikortregister"),
                Party(listOf(PartyId("HER","8090595")),"Utleverer"),
                "HarBorgerEgenandelFritak","EgenandelForesporsel")
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

    @Test
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
    fun `Delete CPA should result in deletion`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = httpClient.delete("/cpa/delete/nav:qass:35065")
        assertEquals("nav:qass:35065 slettet!", response.bodyAsText())
    }
}
