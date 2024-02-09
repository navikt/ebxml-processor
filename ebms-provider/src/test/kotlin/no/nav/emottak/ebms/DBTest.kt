package no.nav.emottak.ebms

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.persistence.CPA_DB_NAME
import org.testcontainers.containers.PostgreSQLContainer

fun cpaPostgres(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
        withUsername("$CPA_DB_NAME-admin")
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
