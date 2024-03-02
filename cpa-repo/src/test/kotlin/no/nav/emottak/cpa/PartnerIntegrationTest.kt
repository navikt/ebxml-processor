package no.nav.emottak.cpa

import com.zaxxer.hikari.HikariConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.emottak.cpa.persistence.DBTest
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.cpa.persistence.gammel.PARTNER_CPA
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import org.testcontainers.containers.OracleContainer
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class PartnerIntegrationTest : PartnerTest() {

    fun <T> cpaRepoTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application(cpaApplicationModule(db.dataSource, db.dataSource, oracle.dataSource))
        testBlock()
    }

    @Test
    fun `Partner endepunkt Good case`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val herId = "8141253"
        val role = "Behandler"
        val service = "BehandlerKrav"
        val action = "Svarmelding"
        val httpResponse = httpClient.get("/partner/her/$herId?role=$role&service=$service&action=$action")
        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertEquals("9999", httpResponse.body<String>())
    }

    @Test
    fun `Partner endepunkt returnerer 404 når partner id ikke finnes`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val herId = "123"
        val role = "Behandler"
        val service = "BehandlerKrav"
        val action = "Svarmelding"
        val httpResponse = httpClient.get("/partner/her/$herId?role=$role&service=$service&action=$action")
        assertEquals(HttpStatusCode.NotFound, httpResponse.status)
        assertEquals("Fant ikke partnerId for herId $herId", httpResponse.body<String>())
    }

    @Test
    fun `Partner endepunkt returner 400 når role, service, action mangler`() = cpaRepoTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val herId = "123"
        val httpResponse = httpClient.get("/partner/her/$herId")
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)
    }
}

abstract class PartnerTest() : DBTest() {

    protected lateinit var oracle: Database

    @BeforeTest
    fun beforeEach2() {
        val oracleContainer = partnerOracle().also {
            it.start()
        }
        oracle = Database(oracleContainer.testConfiguration()).configurePartnerFlyway()

        val tables = listOf(PARTNER_CPA)
        transaction(oracle.db) {
            tables.forEach { it.deleteAll() }
        }
        transaction(oracle.db) {
            PARTNER_CPA.insert {
                it[partnerId] = 9999u
                it[cpaId] = "nav:qass:35065"
            }
        }
        Thread.sleep(2000)
    }

    fun OracleContainer.testConfiguration(): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = this@testConfiguration.jdbcUrl
            username = this@testConfiguration.username
            password = this@testConfiguration.password
            maximumPoolSize = 5
            minimumIdle = 1
            idleTimeout = 500001
            connectionTimeout = 10000
            maxLifetime = 600001
            initializationFailTimeout = 5000
        }
    }

    fun partnerOracle(): OracleContainer = OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
        .withDatabaseName("testDB")
        .withUsername("testUser")
        .withPassword("testPassword")
        .withReuse(true)

    private fun Database.configurePartnerFlyway(): Database =
        also {
            Flyway.configure()
                .dataSource(it.dataSource)
                .failOnMissingLocations(true)
                .cleanDisabled(false)
                .locations("filesystem:src/test/resources/oracle/migrations")
                .load()
                .also(Flyway::clean)
                .migrate()
        }
}
