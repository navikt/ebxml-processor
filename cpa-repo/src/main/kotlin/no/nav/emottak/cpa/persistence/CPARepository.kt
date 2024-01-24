package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.time.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere

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
            (
                if (idList.isNotEmpty()) {
                    CPA.select(where = { CPA.id inList idList })
                } else {
                    CPA.selectAll()
                }
                )
                .associate {
                    Pair(it[CPA.id], it[CPA.updated_date].toString())
                }
        }
    }

    fun findLatestUpdatedCpaTimestamp(): String {
        return transaction(db = database.db) {
            CPA.select(where = { CPA.updated_date.isNotNull() })
                .orderBy(CPA.updated_date, SortOrder.DESC)
                .first()[CPA.updated_date]
        }.toString()
    }

    fun findCpaEntry(cpaId: String): CpaDbEntry? {
        return transaction(db = database.db) {
            CPA.select(where = {
                CPA.id.eq(cpaId)
            }).firstOrNull()?.let {
                CpaDbEntry(
                    it[CPA.id],
                    it[CPA.cpa],
                    it[CPA.updated_date],
                    it[CPA.entryCreated]
                )
            }
        }
    }

    fun putCpa(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.insert {
                it[CPA.id] = cpa.id
                it[CPA.cpa] = cpa.cpa ?: throw IllegalArgumentException("Kan ikke sette null verdi for CPA i DB")
                it[CPA.entryCreated] = cpa.create_date
                it[CPA.updated_date] = cpa.updated_date
            }
        }
        return cpa.id
    }

    fun upsertCpa(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.upsert(CPA.id) {
                it[CPA.id] = cpa.id
                it[CPA.cpa] = cpa.cpa ?: throw IllegalArgumentException("Kan ikke sette null verdi for CPA i DB")
                it[CPA.entryCreated] = cpa.create_date
                it[CPA.updated_date] = cpa.updated_date
            }
        }
        return cpa.id
    }

    fun deleteCpa(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.deleteWhere { CPA.id.eq(cpa.id) }
        }
        return cpa.id
    }

    data class CpaDbEntry(
        val id: String,
        val cpa: CollaborationProtocolAgreement? = null,
        val updated_date: Instant, // OBS! Truncates til seconds av praktiske hensyn.
        val create_date: Instant // OBS! Truncates til seconds av praktiske hensyn.
    )

    // @Serializable
    // data class TimestampResponse(
    //    val idMap: Map<String, String>
    // )

    // fun List<Pair<>>.toTimestampResponse() {}
}
