package no.nav.emottak.cpa.persistence

import java.time.Instant
import no.nav.emottak.cpa.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class CPARepository(val database: Database) {

    fun findCpa(cpaId: String): CollaborationProtocolAgreement? {
        return transaction(db = database.db) {
            CPA.select(where = {
                CPA.id.eq(cpaId)
            }).firstOrNull()?.get(
                CPA.cpa
            )
        }
    }

    fun findCpaTimestamps(idList: List<String>): Map<String, String> {
        return transaction(db = database.db) {
            (if (idList.isNotEmpty())
                CPA.select(where = { CPA.id inList idList })
            else
                CPA.selectAll())
                .associate {
                    Pair(it[CPA.id], it[CPA.activeSince].toString())
                }
        }
    }

    fun findCpaEntry(cpaId: String): CpaDbEntry? {
        return transaction(db = database.db) {
            CPA.select(where = {
                CPA.id.eq(cpaId)
            }).firstOrNull()?.let {
                CpaDbEntry(
                    it[CPA.id],
                    it[CPA.cpa],
                    it[CPA.activeSince],
                    it[CPA.entryCreated],
                )
            }
        }
    }

    fun putCpa(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.insert {
                it[CPA.id] = cpa.id
                it[CPA.cpa] = cpa.cpa ?: throw IllegalArgumentException("Kan ikke sette null verdi for CPA i DB")
                it[CPA.entryCreated] = cpa.entryCreated
                it[CPA.activeSince] = cpa.activeSince
            }
        }
        return cpa.id
    }

    data class CpaDbEntry(
        val id: String,
        val cpa: CollaborationProtocolAgreement? = null,
        val activeSince: Instant,
        val entryCreated: Instant
    )

    //@Serializable
    //data class TimestampResponse(
    //    val idMap: Map<String, String>
    //)

    //fun List<Pair<>>.toTimestampResponse() {}
}