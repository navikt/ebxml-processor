package no.nav.emottak.cpa.databasetest.setup

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.cpa.getPartnerPartyIdByType
import no.nav.emottak.cpa.persistence.CPA
import no.nav.emottak.cpa.persistence.CPA_DB_NAME
import no.nav.emottak.cpa.persistence.Database
import no.nav.emottak.cpa.xmlMarshaller
import no.nav.emottak.message.ebxml.PartyTypeEnum
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresTestSetup {
    lateinit var timestamp: Instant
    var isInitialized = false

    fun initialize(): Database {
        timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:14").apply {
            withUsername("$CPA_DB_NAME-admin")
            withReuse(true)
            withLabel("app-navn", "cpa-repo")
            start()
            println(
                "Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
            )
        }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
            maximumPoolSize = 5
            minimumIdle = 1
            idleTimeout = 500001
            connectionTimeout = 10000
            maxLifetime = 600001
            initializationFailTimeout = 5000
        }

        val postgres = Database(hikariConfig)

        Flyway.configure()
            .dataSource(postgres.dataSource)
            .failOnMissingLocations(true)
            .cleanDisabled(false)
            .load()
            .also(Flyway::clean)
            .migrate()

        isInitialized = true

        return postgres
    }

    fun prepareTestData(postgres: Database) {
        if (!isInitialized) {
            throw RuntimeException("You cannot prepare test data before running initialize()")
        }

        val tables = listOf(CPA)
        transaction(postgres.db) {
            tables.forEach { it.deleteAll() }
        }

        val cpasToInsert = listOf("nav-qass-35065.xml", "nav-qass-31162.xml", "nav-qass-31162_multipleChannels_and_multiple_endpoints.xml")
        transaction(postgres.db) {
            cpasToInsert.forEach { cpaToInsert ->
                CPA.insert {
                    val collaborationProtocolAgreement = loadTestCPA(cpaToInsert)
                    it[id] = collaborationProtocolAgreement.cpaid
                    it[cpa] = collaborationProtocolAgreement
                    it[updated_date] = timestamp
                    it[entryCreated] = timestamp
                    it[herId] = collaborationProtocolAgreement.getPartnerPartyIdByType(PartyTypeEnum.HER)?.value
                }
            }
        }
    }

    private fun loadTestCPA(cpaName: String): CollaborationProtocolAgreement {
        val testCpaString = String(this::class.java.classLoader.getResource("cpa/$cpaName").readBytes())
        return xmlMarshaller.unmarshal(testCpaString, CollaborationProtocolAgreement::class.java)
    }
}
