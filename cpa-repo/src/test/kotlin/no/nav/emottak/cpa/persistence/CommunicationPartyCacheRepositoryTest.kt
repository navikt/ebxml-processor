package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.databasetest.PostgresTest
import no.nav.emottak.cpa.nhn.adresseregisteret.model.CommunicationParty
import no.nav.emottak.cpa.nhn.adresseregisteret.model.CurrentCertificate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommunicationPartyCacheRepositoryTest : PostgresTest() {

    private val herId = 8141253L

    private fun communicationParty(name: String) = CommunicationParty(
        herId = herId,
        name = name,
        type = "Organization",
        currentEncryptionCertificate = CurrentCertificate("thumb", "2020-01-01", "2030-01-01")
    )

    @AfterTest
    fun cleanup() {
        transaction(postgres.db) {
            CommunicationPartyCacheTable.deleteAll()
        }
    }

    @Test
    fun `returnerer null for HerId som ikke finnes i cache`() {
        val repository = CommunicationPartyCacheRepository(postgres)
        assertNull(repository.findCommunicationParty(herId))
    }

    @Test
    fun `returnerer cachet CommunicationParty som er fersk`() {
        val repository = CommunicationPartyCacheRepository(postgres, cacheTtl = Duration.ofDays(1))
        val party = communicationParty("Testparty")
        repository.upsertCommunicationParty(herId, party)
        assertEquals(party, repository.findCommunicationParty(herId))
    }

    @Test
    fun `returnerer null når cachet CommunicationParty er utgått`() {
        val repository = CommunicationPartyCacheRepository(postgres, cacheTtl = Duration.ofMillis(1))
        repository.upsertCommunicationParty(herId, communicationParty("Testparty"))
        // Sørger for at oppføringen betraktes som gammel uavhengig av eksekveringstid
        transaction(postgres.db) {
            CommunicationPartyCacheTable.update({ CommunicationPartyCacheTable.herId eq herId }) {
                it[lastUpdated] = Instant.now().minus(Duration.ofDays(2))
            }
        }
        assertNull(repository.findCommunicationParty(herId))
    }

    @Test
    fun `oppdaterer kun berørt felt ved upsert`() {
        val repository = CommunicationPartyCacheRepository(postgres)
        val party = communicationParty("Testparty")
        repository.upsertCommunicationParty(herId, party)
        assertNull(repository.findSigningCertificate(herId))
        assertEquals(party, repository.findCommunicationParty(herId))
    }
}
