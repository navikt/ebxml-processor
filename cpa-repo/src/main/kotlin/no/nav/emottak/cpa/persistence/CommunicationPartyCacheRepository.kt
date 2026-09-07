package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.nhn.adresseregisteret.model.Certificate
import no.nav.emottak.cpa.nhn.adresseregisteret.model.CommunicationParty
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Duration
import java.time.Instant

/**
 * Caches [CommunicationParty] and certificate lookups against the NHN Adresseregister so that
 * repeated requests for the same HerId don't need to hit the external API every time. A cached
 * value is considered valid for [cacheTtl] after it was last refreshed; once stale (or missing),
 * callers should fetch fresh data from the API and store it again via the corresponding
 * `upsert*` method.
 */
class CommunicationPartyCacheRepository(
    val database: Database,
    private val cacheTtl: Duration = Duration.ofDays(1)
) {
    private data class CacheRow(
        val communicationParty: CommunicationParty?,
        val signingCert: Certificate?,
        val encryptionCert: Certificate?,
        val lastUpdated: Instant
    )

    private fun findRow(herId: Long): CacheRow? =
        transaction(database.db) {
            CommunicationPartyCacheTable.selectAll()
                .where { CommunicationPartyCacheTable.herId eq herId }
                .firstOrNull()
                ?.let {
                    CacheRow(
                        it[CommunicationPartyCacheTable.communicationParty],
                        it[CommunicationPartyCacheTable.signingCert],
                        it[CommunicationPartyCacheTable.encryptionCert],
                        it[CommunicationPartyCacheTable.lastUpdated]
                    )
                }
        }

    private fun CacheRow.isFresh(): Boolean = Duration.between(lastUpdated, Instant.now()) <= cacheTtl

    fun findCommunicationParty(herId: Long): CommunicationParty? =
        findRow(herId)?.takeIf { it.isFresh() }?.communicationParty

    fun findSigningCertificate(herId: Long): Certificate? =
        findRow(herId)?.takeIf { it.isFresh() }?.signingCert

    fun findEncryptionCertificate(herId: Long): Certificate? =
        findRow(herId)?.takeIf { it.isFresh() }?.encryptionCert

    fun upsertCommunicationParty(herId: Long, value: CommunicationParty) {
        transaction(database.db) {
            CommunicationPartyCacheTable.upsert(
                CommunicationPartyCacheTable.herId,
                onUpdateExclude = listOf(CommunicationPartyCacheTable.herId)
            ) {
                it[CommunicationPartyCacheTable.herId] = herId
                it[communicationParty] = value
                it[lastUpdated] = Instant.now()
            }
        }
    }

    fun upsertSigningCertificate(herId: Long, value: Certificate) {
        transaction(database.db) {
            CommunicationPartyCacheTable.upsert(
                CommunicationPartyCacheTable.herId,
                onUpdateExclude = listOf(CommunicationPartyCacheTable.herId)
            ) {
                it[CommunicationPartyCacheTable.herId] = herId
                it[signingCert] = value
                it[lastUpdated] = Instant.now()
            }
        }
    }

    fun upsertEncryptionCertificate(herId: Long, value: Certificate) {
        transaction(database.db) {
            CommunicationPartyCacheTable.upsert(
                CommunicationPartyCacheTable.herId,
                onUpdateExclude = listOf(CommunicationPartyCacheTable.herId)
            ) {
                it[CommunicationPartyCacheTable.herId] = herId
                it[encryptionCert] = value
                it[lastUpdated] = Instant.now()
            }
        }
    }
}
