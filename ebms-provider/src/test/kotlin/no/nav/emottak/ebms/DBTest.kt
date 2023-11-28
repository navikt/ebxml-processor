package no.nav.emottak.ebms

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.Database
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

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
fun dbConfig(): HikariConfig {
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


