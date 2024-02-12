package no.nav.emottak.cpa

import com.zaxxer.hikari.HikariConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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
        val partnerId = httpClient.get("/partner/8141253").body<String>()
        println(partnerId)
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
