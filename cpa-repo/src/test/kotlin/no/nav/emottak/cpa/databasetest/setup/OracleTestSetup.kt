package no.nav.emottak.cpa.databasetest.setup

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.persistence.Database
import org.flywaydb.core.Flyway
import org.testcontainers.containers.OracleContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

class OracleTestSetup {
    lateinit var timestamp: Instant
    var isInitialized = false

    fun initialize(): Database {
        timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val oracleContainer = OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .apply {
                withDatabaseName("testDB")
                withUsername("testUser")
                withPassword("testPassword")
                withReuse(true)
                start()
            }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = oracleContainer.jdbcUrl
            username = oracleContainer.username
            password = oracleContainer.password
            maximumPoolSize = 5
            minimumIdle = 1
            idleTimeout = 500001
            connectionTimeout = 10000
            maxLifetime = 600001
            initializationFailTimeout = 5000
        }

        val oracle = Database(hikariConfig)

        Flyway.configure()
            .dataSource(oracle.dataSource)
            .failOnMissingLocations(true)
            .cleanDisabled(false)
            .locations("filesystem:src/test/resources/oracle/migrations")
            .load()
            .also(Flyway::clean)
            .migrate()

        isInitialized = true

        return oracle
    }
}
