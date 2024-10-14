package no.nav.emottak.ebms.test

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import kotlinx.coroutines.runBlocking
import no.nav.emottak.constants.SMTPHeaders
import no.nav.emottak.cpa.cpaApplicationModule
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.ebms.cpaPostgres
import no.nav.emottak.ebms.defaultHttpClient
import no.nav.emottak.ebms.ebmsProviderModule
import no.nav.emottak.ebms.testConfiguration
import no.nav.emottak.ebms.validation.MimeHeaders
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

open class EndToEndTest {
    companion object {
        val portnoEbmsProvider = 8089
        val portnoCpaRepo = 8088
        val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        val ebmsProviderUrl = "http://localhost:$portnoEbmsProvider"
        val cpaRepoUrl = "http://localhost:$portnoCpaRepo"

        // TODO Start mailserver og payload processor
        val cpaDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderServer: ApplicationEngine
        lateinit var cpaRepoServer: ApplicationEngine
        init {
            cpaDbContainer = cpaPostgres()
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            System.setProperty("CPA_REPO_URL", cpaRepoUrl)
            cpaDbContainer.start()
            val db = Database(cpaDbContainer.testConfiguration())
                .also {
                    Flyway.configure()
                        .dataSource(it.dataSource)
                        .failOnMissingLocations(true)
                        .cleanDisabled(false)
                        .load()
                        .also(Flyway::clean)
                        .migrate()
                }
            cpaRepoServer = embeddedServer(
                Netty,
                port = portnoCpaRepo,
                module = cpaApplicationModule(db.dataSource, db.dataSource)
            ).also {
                it.start()
            }
            ebmsProviderServer = embeddedServer(Netty, port = portnoEbmsProvider, module = { ebmsProviderModule() }).also {
                it.start()
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            ebmsProviderServer.stop()
            cpaRepoServer.stop()
        }
    }
}

class IntegrasjonsTest2 : EndToEndTest() {
    @Test
    fun testMe() {
        println("Tested")
    }
}
class IntegrasjonsTest : EndToEndTest() {

    @Test
    fun basicEndpointTest() = testApplication {
        application { ebmsProviderModule() }
        val response = client.get("/")
        Assertions.assertEquals(HttpStatusCode.OK, response.status)
        Assertions.assertEquals("Hello, world!", response.bodyAsText())
    }

    @Test
    fun testAlleIntegrasjoner() {
        // TODO insert in cpa repo en cpa test oauth2
        clearAllMocks()
        val httpClient = defaultHttpClient().invoke()
        runBlocking {
            val post = httpClient.post("$ebmsProviderUrl/ebms/async") {
                mockMultipartRequest()
                // TODO send en melding som ikke feiler CPA-ID validation
            }
        }
    }

    @Test
    @Disabled // krever naisdevice
    fun testDevFssEndpoint() {
        runBlocking {
            defaultHttpClient()
                .invoke().post("https://ebms-provider.intern.dev.nav.no/ebms/async") {
                    mockMultipartRequest()
                }
        }
    }

    fun HttpRequestBuilder.mockMultipartRequest() {
        val EXAMPLE_BODY =
            "------=_Part_495_-1172936255.1665395092859\r\nContent-Type: text/xml\r\nContent-Transfer-Encoding: base64\r\nContent-ID: <soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>\r\n\r\nPFNPQVA6RW52ZWxvcGUgeG1sbnM6U09BUD0iaHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvc29h\r\ncC9lbnZlbG9wZS8iIHhtbG5zOmViPSJodHRwOi8vd3d3Lm9hc2lzLW9wZW4ub3JnL2NvbW1pdHRl\r\nZXMvZWJ4bWwtbXNnL3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54c2QiIHhtbG5zOnhsaW5rPSJodHRw\r\nOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB4bWxuczp4c2k9Imh0dHA6Ly93d3cudzMub3JnLzIw\r\nMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4c2k6c2NoZW1hTG9jYXRpb249Imh0dHA6Ly9zY2hlbWFz\r\nLnhtbHNvYXAub3JnL3NvYXAvZW52ZWxvcGUvIGh0dHA6Ly93d3cub2FzaXMtb3Blbi5vcmcvY29t\r\nbWl0dGVlcy9lYnhtbC1tc2cvc2NoZW1hL2VudmVsb3BlLnhzZCBodHRwOi8vd3d3Lm9hc2lzLW9w\r\nZW4ub3JnL2NvbW1pdHRlZXMvZWJ4bWwtbXNnL3NjaGVtYS9tc2ctaGVhZGVyLTJfMC54c2QiPjxT\r\nT0FQOkhlYWRlcj48ZWI6TWVzc2FnZUhlYWRlciBTT0FQOm11c3RVbmRlcnN0YW5kPSIxIiBlYjp2\r\nZXJzaW9uPSIyLjAiPjxlYjpGcm9tPjxlYjpQYXJ0eUlkIGViOnR5cGU9IkhFUiI+ODgzNjQ8L2Vi\r\nOlBhcnR5SWQ+PGViOlJvbGU+VXRsZXZlcmVyPC9lYjpSb2xlPjwvZWI6RnJvbT48ZWI6VG8+PGVi\r\nOlBhcnR5SWQgZWI6dHlwZT0iSEVSIj43OTc2ODwvZWI6UGFydHlJZD48ZWI6Um9sZT5Gcmlrb3J0\r\ncmVnaXN0ZXI8L2ViOlJvbGU+PC9lYjpUbz48ZWI6Q1BBSWQ+bmF2OnFhc3M6MzE4Njg8L2ViOkNQ\r\nQUlkPjxlYjpDb252ZXJzYXRpb25JZD5hYjFjOWI0Mi04ZDI5LTQ5YWQtYjg3MS1iNDc4OTFlMDNm\r\nODg8L2ViOkNvbnZlcnNhdGlvbklkPjxlYjpTZXJ2aWNlIGViOnR5cGU9InN0cmluZyI+SGFyQm9y\r\nZ2VyRWdlbmFuZGVsRnJpdGFrPC9lYjpTZXJ2aWNlPjxlYjpBY3Rpb24+RWdlbmFuZGVsRm9yZXNw\r\nb3JzZWw8L2ViOkFjdGlvbj48ZWI6TWVzc2FnZURhdGE+PGViOk1lc3NhZ2VJZD5hYjFjOWI0Mi04\r\nZDI5LTQ5YWQtYjg3MS1iNDc4OTFlMDNmODg8L2ViOk1lc3NhZ2VJZD48ZWI6VGltZXN0YW1wPjIw\r\nMjItMTAtMTBUMDk6NDQ6NTIuNDMxWjwvZWI6VGltZXN0YW1wPjwvZWI6TWVzc2FnZURhdGE+PC9l\r\nYjpNZXNzYWdlSGVhZGVyPjxlYjpTeW5jUmVwbHkgU09BUDphY3Rvcj0iaHR0cDovL3NjaGVtYXMu\r\neG1sc29hcC5vcmcvc29hcC9hY3Rvci9uZXh0IiBTT0FQOm11c3RVbmRlcnN0YW5kPSIxIiBlYjp2\r\nZXJzaW9uPSIyLjAiPjwvZWI6U3luY1JlcGx5PjxTaWduYXR1cmUgeG1sbnM9Imh0dHA6Ly93d3cu\r\ndzMub3JnLzIwMDAvMDkveG1sZHNpZyMiPjxTaWduZWRJbmZvPjxDYW5vbmljYWxpemF0aW9uTWV0\r\naG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvVFIvMjAwMS9SRUMteG1sLWMxNG4tMjAw\r\nMTAzMTUiPjwvQ2Fub25pY2FsaXphdGlvbk1ldGhvZD48U2lnbmF0dXJlTWV0aG9kIEFsZ29yaXRo\r\nbT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI3JzYS1zaGExIj48L1NpZ25hdHVy\r\nZU1ldGhvZD48UmVmZXJlbmNlIFVSST0iIj48VHJhbnNmb3Jtcz48VHJhbnNmb3JtIEFsZ29yaXRo\r\nbT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI2VudmVsb3BlZC1zaWduYXR1cmUi\r\nPjwvVHJhbnNmb3JtPjxUcmFuc2Zvcm0gQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy9UUi8y\r\nMDAxL1JFQy14bWwtYzE0bi0yMDAxMDMxNSI+PC9UcmFuc2Zvcm0+PC9UcmFuc2Zvcm1zPjxEaWdl\r\nc3RNZXRob2QgQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwLzA5L3htbGRzaWcjc2hh\r\nMSI+PC9EaWdlc3RNZXRob2Q+PERpZ2VzdFZhbHVlPlgyalNJL1VieG5LSGpjMXpVV053eHpBUEc0\r\ndz08L0RpZ2VzdFZhbHVlPjwvUmVmZXJlbmNlPjxSZWZlcmVuY2UgVVJJPSJjaWQ6YXR0YWNobWVu\r\ndElkLTAyMmZhZjI2LTBlMTItNGRmZS1hNTliLWI1MDYxMzJmMTY0NSI+PERpZ2VzdE1ldGhvZCBB\r\nbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNzaGExIj48L0RpZ2Vz\r\ndE1ldGhvZD48RGlnZXN0VmFsdWU+djlQSGVqMlNnV2x3d1FRQVZFTlVEMG80UjRVPTwvRGlnZXN0\r\nVmFsdWU+PC9SZWZlcmVuY2U+PC9TaWduZWRJbmZvPjxTaWduYXR1cmVWYWx1ZT5Nb1QyeUxvY0pM\r\nb2R2WGE3dzEwTnJDMFpaM1ZlR3FvSk9QUXlsZG1tampGZ0JxdXBqU0xiQmFIYVgyYWFzNFFPL3FS\r\nRGx6Vk9ubDZ2bERkRWJ5LzBvMjdMWkppZEx2dEhXRWN6WEpVR1V3ZXNhY1V1M2hjbUZodWJVSTlh\r\nbXdvTVpmelRaaVlIYzZPTlowUVZXcE1rdlRVWUdsSXIvS0V5TmFFeGZ1WWVQKzYxWXdwMlB0UjFp\r\nbmVOaUgrWmpjOWpYZStWMHUyaVFtL3pzSzRUZ2VtdDRva3BTWkRCd2RUSzFUQ1h1UThRKzVKOTBz\r\neDUwWU5Bb0VLbEgrMkdkeFRYRWxaZ3RHSmdQOCtYYVBKZzB5VWJmWkxhREJXZDhyMGljSTlUamM2\r\naXdBUkczT2JoTS9iNGdqZ3pQU0NIS2hIbWJJWkVGWkZ4bUtaQUU3Mk1NejhHY1E9PTwvU2lnbmF0\r\ndXJlVmFsdWU+PEtleUluZm8+PFg1MDlEYXRhPjxYNTA5Q2VydGlmaWNhdGU+TUlJRlFUQ0NCQ21n\r\nQXdJQkFnSUxCTmdKQTZINDhBN1JLUGN3RFFZSktvWklodmNOQVFFTEJRQXdVVEVMTUFrR0ExVUVC\r\naE1DVGs4eEhUQWJCZ05WQkFvTUZFSjFlWEJoYzNNZ1FWTXRPVGd6TVRZek16STNNU013SVFZRFZR\r\nUUREQnBDZFhsd1lYTnpJRU5zWVhOeklETWdWR1Z6ZERRZ1EwRWdNekFlRncweU1UQTVNVFl4TmpV\r\nM05EaGFGdzB5TkRBNU1UWXlNVFU1TURCYU1JR0hNUXN3Q1FZRFZRUUdFd0pPVHpFWE1CVUdBMVVF\r\nQ2d3T1FrOVBWRk1nVGs5U1IwVWdRVk14SnpBbEJnTlZCQXNNSGtKUFQxUlRJRUZRVDFSRlN5QlRT\r\nOE9ZV1VWT0xUazNPVFF3TmpreU5ERWlNQ0FHQTFVRUF3d1pRazlQVkZNZ1FWQlBWRVZMSUZOTHc1\r\naFpSVTRnVkVWVFZERVNNQkFHQTFVRUJSTUpPVGd5TlRRM09ESXlNSUlCSWpBTkJna3Foa2lHOXcw\r\nQkFRRUZBQU9DQVE4QU1JSUJDZ0tDQVFFQXAvaWtLbUhJaGRpRWw5d04zZGwvSVI1Y0RWK3o1R0RK\r\nU1NhQnR5QUZUZGdTdGVYQ3pPU0tpVitPMkgvMTVYN1NiNGkzK2gyb3FuNlpQdnFza2FYbE1GUXFk\r\nZlBOZnI2QVFsYVdhdlBQZ0w1dWJPeXFjSjdjMlZ6bzZMRlQ2WHNyaFN5MW4rcnozTXVRSUNqdjdV\r\nMzRCNnArc3JKbDh0OW9mNUVwOWJsUGo4bXR5OWZaU2JpZis3b1hxa2FBQVV5TFQ4T2hyUmhaWlR4\r\nQVdDWDMvLzNXbEtqMk1udk4xR0JZK3NjUDk4RjRoM0dyTFBiMEN3RHJJWVJrZDJhYlNHVUhUZXRo\r\nV1lNamtRbVp6YkVDQk45K1Z1cnpaY0VyUVhKMm9XcHF2aW1PYkJWUW5hc1ZZRjkvWE5KZlBnNVBP\r\nUGJoU3BsQ0pxRzFCcWpyODdudmFmM1RSd0lEQVFBQm80SUI0VENDQWQwd0NRWURWUjBUQkFJd0FE\r\nQWZCZ05WSFNNRUdEQVdnQlEvcnZWNEM1S2pjQ0ExWDFyNjl5U2dVZ0h3UVRBZEJnTlZIUTRFRmdR\r\nVXF1YWhsU0FSb1h5YnRaaUJsVG1Qc0xubUZaWXdEZ1lEVlIwUEFRSC9CQVFEQWdaQU1CMEdBMVVk\r\nSlFRV01CUUdDQ3NHQVFVRkJ3TUNCZ2dyQmdFRkJRY0RCREFXQmdOVkhTQUVEekFOTUFzR0NXQ0VR\r\nZ0VhQVFBREFqQ0J1d1lEVlIwZkJJR3pNSUd3TURlZ05hQXpoakZvZEhSd09pOHZZM0pzTG5SbGMz\r\nUTBMbUoxZVhCaGMzTXVibTh2WTNKc0wwSlFRMnhoYzNNelZEUkRRVE11WTNKc01IV2djNkJ4aG05\r\nc1pHRndPaTh2YkdSaGNDNTBaWE4wTkM1aWRYbHdZWE56TG01dkwyUmpQVUoxZVhCaGMzTXNaR005\r\nVGs4c1EwNDlRblY1Y0dGemN5VXlNRU5zWVhOekpUSXdNeVV5TUZSbGMzUTBKVEl3UTBFbE1qQXpQ\r\nMk5sY25ScFptbGpZWFJsVW1WMmIyTmhkR2x2Ymt4cGMzUXdnWW9HQ0NzR0FRVUZCd0VCQkg0d2ZE\r\nQTdCZ2dyQmdFRkJRY3dBWVl2YUhSMGNEb3ZMMjlqYzNBdWRHVnpkRFF1WW5WNWNHRnpjeTV1Ynk5\r\ndlkzTndMMEpRUTJ4aGMzTXpWRFJEUVRNd1BRWUlLd1lCQlFVSE1BS0dNV2gwZEhBNkx5OWpjblF1\r\nZEdWemREUXVZblY1Y0dGemN5NXVieTlqY25RdlFsQkRiR0Z6Y3pOVU5FTkJNeTVqWlhJd0RRWUpL\r\nb1pJaHZjTkFRRUxCUUFEZ2dFQkFDek9xUGZBUlgvZVE3Ry92M1EzUlo1bkJuUi9uTjQ2WWx5VzBj\r\nU1hOMEZKa1pMeGJWZThBNUUvSTFRdjdFYUFvdEwyV0VJV1FJZlhCdkRqTDZ6UmVkUEpEYXRjUEI5\r\ndHR0bGpta1JMSENzSUdDZTRpUndURGFqNEVYZHVWOU1rdVBBUklWNWFLRGpmMC8wdTl4Yk54SFJQ\r\nWTNTb2FJNmRzUEVhb1VjWGt0QnVMTGJrV3ZFQXo1c2xvRU8wRGp1eGY2V1pRZDdGeWcyd2l2Sllt\r\nUWwvNzVkTGVEYUFUS092YWdTMWtZemtqeHRsd09LbFZ2Q21ZSTFicFlSRnl4Y2lDWGVYL09BRU9L\r\nakNqM0FkdlBzUVdMOERyeFVQYVdxS0RzcGxWRlJod3NldFQ1UW5YejdKazNNY0hBS21KNUFuYXdM\r\nMXE2RmdxRG5CcWdiOGQrZUtTU2M9PC9YNTA5Q2VydGlmaWNhdGU+PC9YNTA5RGF0YT48S2V5VmFs\r\ndWU+PFJTQUtleVZhbHVlPjxNb2R1bHVzPnAvaWtLbUhJaGRpRWw5d04zZGwvSVI1Y0RWK3o1R0RK\r\nU1NhQnR5QUZUZGdTdGVYQ3pPU0tpVitPMkgvMTVYN1NiNGkzK2gyb3FuNlpQdnFza2FYbE1GUXFk\r\nZlBOZnI2QVFsYVdhdlBQZ0w1dWJPeXFjSjdjMlZ6bzZMRlQ2WHNyaFN5MW4rcnozTXVRSUNqdjdV\r\nMzRCNnArc3JKbDh0OW9mNUVwOWJsUGo4bXR5OWZaU2JpZis3b1hxa2FBQVV5TFQ4T2hyUmhaWlR4\r\nQVdDWDMvLzNXbEtqMk1udk4xR0JZK3NjUDk4RjRoM0dyTFBiMEN3RHJJWVJrZDJhYlNHVUhUZXRo\r\nV1lNamtRbVp6YkVDQk45K1Z1cnpaY0VyUVhKMm9XcHF2aW1PYkJWUW5hc1ZZRjkvWE5KZlBnNVBP\r\nUGJoU3BsQ0pxRzFCcWpyODdudmFmM1RSdz09PC9Nb2R1bHVzPjxFeHBvbmVudD5BUUFCPC9FeHBv\r\nbmVudD48L1JTQUtleVZhbHVlPjwvS2V5VmFsdWU+PC9LZXlJbmZvPjwvU2lnbmF0dXJlPjwvU09B\r\nUDpIZWFkZXI+PFNPQVA6Qm9keT48ZWI6TWFuaWZlc3QgZWI6dmVyc2lvbj0iMi4wIj48ZWI6UmVm\r\nZXJlbmNlIHhsaW5rOmhyZWY9ImNpZDphdHRhY2htZW50SWQtMDIyZmFmMjYtMGUxMi00ZGZlLWE1\r\nOWItYjUwNjEzMmYxNjQ1Ij48L2ViOlJlZmVyZW5jZT48L2ViOk1hbmlmZXN0PjwvU09BUDpCb2R5\r\nPjwvU09BUDpFbnZlbG9wZT4=\r\n------=_Part_495_-1172936255.1665395092859\r\nContent-Type: application/pkcs7-mime\r\nContent-Id: <attachmentId-022faf26-0e12-4dfe-a59b-b506132f1645>\r\nContent-Disposition: attachment\r\nContent-Transfer-Encoding: base64\r\n\r\nPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz48bnM6TXNnSGVhZCB4bWxuczp4\r\nc2Q9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4bWxuczp4c2k9Imh0dHA6Ly93\r\nd3cudzMub3JnLzIwMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4bWxuczpucz0iaHR0cDovL3d3dy5r\r\naXRoLm5vL3htbHN0ZHMvbXNnaGVhZC8yMDA2LTA1LTI0IiB4c2k6c2NoZW1hTG9jYXRpb249Imh0\r\ndHA6Ly93d3cua2l0aC5uby94bWxzdGRzL21zZ2hlYWQvMjAwNi0wNS0yNCBNc2dIZWFkLXYxXzIu\r\neHNkIj4KICA8bnM6TXNnSW5mbz4KICAgIDxuczpUeXBlIFY9IkVnZW5hbmRlbEZvcmVzcG9yc2Vs\r\nVjIiIEROPSJGb3Jlc3DDuHJzZWwgb20gZWdlbmFuZGVsIiAvPgogICAgPG5zOk1JR3ZlcnNpb24+\r\ndjEuMiAyMDA2LTA1LTI0PC9uczpNSUd2ZXJzaW9uPgogICAgPG5zOkdlbkRhdGU+MjAyMi0xMC0x\r\nMFQwOTo0NDo1Mi40MzFaPC9uczpHZW5EYXRlPgogICAgPG5zOk1zZ0lkPmFiMWM5YjQyLThkMjkt\r\nNDlhZC1iODcxLWI0Nzg5MWUwM2Y4ODwvbnM6TXNnSWQ+CiAgICA8bnM6Q29udmVyc2F0aW9uUmVm\r\nPgogICAgICA8bnM6UmVmVG9QYXJlbnQ+YWIxYzliNDItOGQyOS00OWFkLWI4NzEtYjQ3ODkxZTAz\r\nZjg4PC9uczpSZWZUb1BhcmVudD4KICAgICAgPG5zOlJlZlRvQ29udmVyc2F0aW9uPmFiMWM5YjQy\r\nLThkMjktNDlhZC1iODcxLWI0Nzg5MWUwM2Y4ODwvbnM6UmVmVG9Db252ZXJzYXRpb24+CiAgICA8\r\nL25zOkNvbnZlcnNhdGlvblJlZj4KICAgIDxuczpTZW5kZXI+CiAgICAgIDxuczpPcmdhbmlzYXRp\r\nb24+CiAgICAgICAgPG5zOk9yZ2FuaXNhdGlvbk5hbWU+Qk9PVFMgQVBPVEVLIFNLw5hZRU48L25z\r\nOk9yZ2FuaXNhdGlvbk5hbWU+CiAgICAgICAgPG5zOklkZW50PgogICAgICAgICAgPG5zOklkPjk3\r\nOTQwNjkyNDwvbnM6SWQ+CiAgICAgICAgICA8bnM6VHlwZUlkIFY9IkVOSCIgUz0iMi4xNi41Nzgu\r\nMS4xMi40LjEuMS45MDUxIiBETj0iT3JnYW5pc2Fzam9uc251bW1lcmV0IGkgRW5oZXRzcmVnaXN0\r\nZXIiIC8+CiAgICAgICAgPC9uczpJZGVudD4KICAgICAgICA8bnM6SWRlbnQ+CiAgICAgICAgICA8\r\nbnM6SWQ+ODgzNjQ8L25zOklkPgogICAgICAgICAgPG5zOlR5cGVJZCBWPSJIRVIiIFM9IjIuMTYu\r\nNTc4LjEuMTIuNC4xLjEuOTA1MSIgRE49IkhFUi1pZCIgLz4KICAgICAgICA8L25zOklkZW50Pgog\r\nICAgICA8L25zOk9yZ2FuaXNhdGlvbj4KICAgIDwvbnM6U2VuZGVyPgogICAgPG5zOlJlY2VpdmVy\r\nPgogICAgICA8bnM6T3JnYW5pc2F0aW9uPgogICAgICAgIDxuczpPcmdhbmlzYXRpb25OYW1lPk5B\r\nVjwvbnM6T3JnYW5pc2F0aW9uTmFtZT4KICAgICAgICA8bnM6SWRlbnQ+CiAgICAgICAgICA8bnM6\r\nSWQ+ODg5NjQwNzgyPC9uczpJZD4KICAgICAgICAgIDxuczpUeXBlSWQgVj0iRU5IIiBTPSIyLjE2\r\nLjU3OC4xLjEyLjQuMS4xLjkwNTEiIEROPSJPcmdhbmlzYXNqb25zbnVtbWVyZXQgaSBFbmhldHNy\r\nZWdpc3RlciIgLz4KICAgICAgICA8L25zOklkZW50PgogICAgICAgIDxuczpJZGVudD4KICAgICAg\r\nICAgIDxuczpJZD43OTc2ODwvbnM6SWQ+CiAgICAgICAgICA8bnM6VHlwZUlkIFY9IkhFUiIgUz0i\r\nMi4xNi41NzguMS4xMi40LjEuMS45MDUxIiBETj0iSWRlbnRpZmlrYXRvciBmcmEgSGVsc2V0amVu\r\nZXN0ZWVuaGV0c3JlZ2lzdGVyZXQiIC8+CiAgICAgICAgPC9uczpJZGVudD4KICAgICAgPC9uczpP\r\ncmdhbmlzYXRpb24+CiAgICA8L25zOlJlY2VpdmVyPgogIDwvbnM6TXNnSW5mbz4KICA8bnM6RG9j\r\ndW1lbnQ+CiAgICA8bnM6RG9jdW1lbnRDb25uZWN0aW9uIFY9IkgiIEROPSJIb3ZlZGRva3VtZW50\r\nIiAvPgogICAgPG5zOlJlZkRvYz4KICAgICAgPG5zOk1zZ1R5cGUgVj0iWE1MIiBETj0iWE1MLWlu\r\nc3RhbnMiIC8+CiAgICAgIDxuczpDb250ZW50PgogICAgICAgIDxFZ2VuYW5kZWxGb3Jlc3BvcnNl\r\nbFYyIHhtbG5zPSJodHRwOi8vd3d3LmtpdGgubm8veG1sc3Rkcy9uYXYvZWdlbmFuZGVsLzIwMTYt\r\nMDYtMTAiPgogICAgICAgICAgPEhhckJvcmdlckVnZW5hbmRlbGZyaXRhaz4KICAgICAgICAgICAg\r\nPEJvcmdlckZucj4xMTAxNzMxOTQzNjwvQm9yZ2VyRm5yPgogICAgICAgICAgICA8RGF0bz4yMDIy\r\nLTEwLTEwPC9EYXRvPgogICAgICAgICAgPC9IYXJCb3JnZXJFZ2VuYW5kZWxmcml0YWs+CiAgICAg\r\nICAgPC9FZ2VuYW5kZWxGb3Jlc3BvcnNlbFYyPgogICAgICA8L25zOkNvbnRlbnQ+CiAgICA8L25z\r\nOlJlZkRvYz4KICA8L25zOkRvY3VtZW50Pgo8L25zOk1zZ0hlYWQ+\r\n\r\n------=_Part_495_-1172936255.1665395092859--"
        val MULTIPART_CONTENT_TYPE =
            """multipart/related;type="text/xml";boundary="----=_Part_495_-1172936255.1665395092859";start="<soapId-6ae68a32-8b0e-4de2-baad-f4d841aacce1>";"""

        this.headers {
            append(MimeHeaders.MIME_VERSION, "1.0")
            append(MimeHeaders.SOAP_ACTION, "ebXML")
            append(MimeHeaders.CONTENT_TYPE, MULTIPART_CONTENT_TYPE)
            append(SMTPHeaders.MESSAGE_ID, "12345")
        }
        this.setBody(EXAMPLE_BODY)
    }
}
