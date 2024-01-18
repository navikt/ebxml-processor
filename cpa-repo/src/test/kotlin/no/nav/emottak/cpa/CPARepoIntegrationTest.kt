package no.nav.emottak.cpa

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import no.nav.emottak.cpa.persistence.CPA
import no.nav.emottak.cpa.persistence.DBTest
import no.nav.emottak.cpa.persistence.DEFAULT_TIMESTAMP
import no.nav.emottak.cpa.persistence.cpaPostgres
import no.nav.emottak.cpa.persistence.testConfiguration
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.melding.model.SignatureDetailsRequest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPARepoIntegrationTest: DBTest() {

    fun <T> cpaRepoTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {

        application (cpaApplicationModule(cpaPostgres().testConfiguration()) )
        testBlock()
    }

    @Test
    fun `Hent sertifikat for signatursjekk`() = cpaRepoTestApp {
        val request = SignatureDetailsRequest(
            cpaId = "nav:qass:35065", //TODO endres hvis/når respons fra getCpa ikke lenger er hardkodet
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
    fun `Hent cpaId map`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        httpClient.post("/cpa"){
            headers {
                header("updated_date", Instant.now().toString())
            }
            setBody(
                xmlMarshaller.marshal(loadTestCPA())
            )
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

}
