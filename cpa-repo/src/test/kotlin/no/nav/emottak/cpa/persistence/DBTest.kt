package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.Database
import no.nav.emottak.cpa.xmlMarshaller
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.BeforeTest

val DEFAULT_TIMESTAMP = Instant.now().truncatedTo(ChronoUnit.SECONDS)
abstract class DBTest() {

    lateinit var db: Database

    @BeforeTest
    fun beforeEach() {
        val posgres = cpaPostgres()
        db = Database(posgres.testConfiguration())
            .configureFlyway()
        val tables = listOf(CPA)
        transaction(db.db) {
            tables.forEach { it.deleteAll() }
        }
        transaction(db.db) {
            CPA.insert {
                val collaborationProtocolAgreement = loadTestCPA()
                it[id] = collaborationProtocolAgreement.cpaid
                it[cpa] = collaborationProtocolAgreement
                it[updated_date] = DEFAULT_TIMESTAMP
                it[entryCreated] = DEFAULT_TIMESTAMP
            }
        }
        Thread.sleep(2000)
    }

    fun loadTestCPA(): CollaborationProtocolAgreement {
        val testCpaString = String(this::class.java.classLoader.getResource("cpa/nav-qass-35065.xml").readBytes())
        return xmlMarshaller.unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
    }
}

private fun Database.configureFlyway(): Database =
    also {
        Flyway.configure()
            .dataSource(it.dataSource)
            .failOnMissingLocations(true)
            .cleanDisabled(false)
            .load()
            .also(Flyway::clean)
            .migrate()
    }

fun cpaPostgres(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
        withReuse(true)
        withLabel("app-navn", "cpa-repo")
        start()
        println(
            "Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
        )
    }

fun PostgreSQLContainer<Nothing>.testConfiguration(): HikariConfig {
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
