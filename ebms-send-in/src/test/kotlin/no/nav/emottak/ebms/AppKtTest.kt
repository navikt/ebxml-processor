package no.nav.emottak.ebms

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import no.nav.emottak.auth.AZURE_AD_AUTH
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppIntegrationTest {
    val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }

    @Test
    fun `Should require valid token`() = testApplication {
        application {
            ebmsSendInModule()
        }
        val token = mockOAuth2Server
            .issueToken(
                AZURE_AD_AUTH,
                "testUser",
                claims = mapOf(
                    "NAVident" to "X112233"
                )
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
        assertEquals("X112233", response.bodyAsText())
    }

    @Test
    fun `Should fail without token`() = testApplication {
        application {
            ebmsSendInModule()
        }
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = httpClient.get("/whoami") {
        }
        assertEquals("X112233", response.bodyAsText())
    }
}
