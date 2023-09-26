package no.nav.emottak.cpa.persistence

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.Database
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.testcontainers.containers.PostgreSQLContainer

abstract class DBTest() {

    @BeforeEach
    fun beforeEach() {
        val db = Database(dbConfig())
            .configureFlyway()
        val tables = listOf(CPA)
        transaction (db.db) {
            tables.forEach { it.deleteAll() }
            CPA.insert {
                it[id] = "123454"
                it[cpa] = CollaborationProtocolAgreement().also {
                    it.cpaid="testCpaID"
                }
            }
        }
    }

}


private fun postgres(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
        withReuse(true)
        withLabel("app-navn", "cpa-repo")
        start()
        println(
            "Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
        )
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

private fun dbConfig(): HikariConfig {
    val postgres = postgres()
    return HikariConfig().apply {
        jdbcUrl = postgres.jdbcUrl
        username = postgres.username
        password = postgres.password
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 500001
        connectionTimeout = 10000
        maxLifetime = 600001
        initializationFailTimeout = 5000
    }
}


