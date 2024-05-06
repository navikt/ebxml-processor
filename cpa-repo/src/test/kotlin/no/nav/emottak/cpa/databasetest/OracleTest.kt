package no.nav.emottak.cpa.databasetest

import no.nav.emottak.cpa.databasetest.setup.OracleTestSetup
import no.nav.emottak.cpa.persistence.Database
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

abstract class OracleTest {
    companion object {
        val oracleTestSetup = OracleTestSetup()
        lateinit var oracle: Database

        @JvmStatic
        @BeforeAll
        fun initializeDatabases() {
            oracle = oracleTestSetup.initialize()
        }
    }

    @BeforeEach
    fun preparePostgresTestData() {
        // None needed (yet)
    }
}
