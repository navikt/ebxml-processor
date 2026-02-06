package no.nav.emottak.cpa.databasetest

import no.nav.emottak.cpa.databasetest.setup.PostgresTestSetup
import no.nav.emottak.cpa.persistence.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.time.Instant

abstract class PostgresTest {
    companion object {
        val postgresTestSetup = PostgresTestSetup()
        lateinit var postgres: Database

        @JvmStatic
        @BeforeAll
        fun initializeDatabases() {
            postgres = postgresTestSetup.initialize()
        }

        @JvmStatic
        @AfterAll
        fun shutdownContainers() {
            postgresTestSetup.postgresContainer.stop()
        }
    }

    @BeforeEach
    fun preparePostgresTestData() {
        postgresTestSetup.prepareTestData(postgres)
    }

    fun getPostgresTimestamp(): Instant {
        return postgresTestSetup.timestamp
    }
}
