package no.nav.emottak.cpa.databasetest

import no.nav.emottak.cpa.databasetest.setup.OracleTestSetup
import no.nav.emottak.cpa.databasetest.setup.PostgresTestSetup
import no.nav.emottak.cpa.persistence.Database
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

abstract class PostgresOracleTest {
    companion object {
        val postgresTestSetup = PostgresTestSetup()
        val oracleTestSetup = OracleTestSetup()

        lateinit var postgres: Database
        lateinit var oracle: Database

        @JvmStatic
        @BeforeAll
        fun initializeDatabases() {
            postgres = postgresTestSetup.initialize()
            oracle = oracleTestSetup.initialize()
        }
    }

    @BeforeEach
    fun preparePostgresTestData() {
        postgresTestSetup.prepareTestData(postgres)
    }
}
