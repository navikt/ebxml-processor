package no.nav.emottak.cpa.persistence

import no.nav.emottak.constants.PartyTypeEnum
import no.nav.emottak.cpa.getPartnerPartyIdByType
import no.nav.emottak.melding.model.ProcessConfig
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.time.Instant

class CPARepository(val database: Database) {

    fun findCpa(cpaId: String): CollaborationProtocolAgreement? {
        return transaction(db = database.db) {
            CPA.selectAll().where {
                CPA.id.eq(cpaId)
            }.firstOrNull()?.get(
                CPA.cpa
            )
        }
    }

    fun findCpaTimestamps(idList: List<String>): Map<String, String> {
        return transaction(db = database.db) {
            (
                if (idList.isNotEmpty()) {
                    CPA.selectAll().where { CPA.id inList idList }
                } else {
                    CPA.selectAll()
                }
                )
                .associate {
                    Pair(
                        it[CPA.id],
                        (
                            it[CPA.updated_date]
                                ?: Instant.ofEpochSecond(0) // False positive
                            ).toString()
                    )
                }
        }
    }

    fun findLatestUpdatedCpaTimestamp(): String {
        return transaction(db = database.db) {
            CPA.selectAll().where { CPA.updated_date.isNotNull() }
                .orderBy(CPA.updated_date, SortOrder.DESC)
                .first()[CPA.updated_date]
        }.toString()
    }

    fun findCpaEntry(cpaId: String): CpaDbEntry? {
        return transaction(db = database.db) {
            CPA.selectAll().where {
                CPA.id.eq(cpaId)
            }.firstOrNull()?.let {
                CpaDbEntry(
                    it[CPA.id],
                    it[CPA.cpa],
                    it[CPA.updated_date],
                    it[CPA.entryCreated],
                    it[CPA.herId]
                )
            }
        }
    }

    fun putCpa(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.insert {
                it[CPA.id] = cpa.id
                it[CPA.cpa] = cpa.cpa ?: throw IllegalArgumentException("Kan ikke sette null verdi for CPA i DB")
                it[CPA.entryCreated] = cpa.createdDate
                it[CPA.updated_date] = cpa.updatedDate
                it[CPA.herId] = cpa.herId
            }
        }
        return cpa.id
    }

    fun upsertCpa(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.upsert(CPA.id) {
                it[CPA.id] = cpa.id
                it[CPA.cpa] = cpa.cpa ?: throw IllegalArgumentException("Kan ikke sette null verdi for CPA i DB")
                it[CPA.entryCreated] = cpa.createdDate
                it[CPA.updated_date] = cpa.updatedDate
                it[CPA.herId] = cpa.herId
            }
        }
        return cpa.id
    }

    fun deleteCpa(cpaId: String): String {
        transaction(database.db) {
            CPA.deleteWhere { CPA.id.eq(cpaId) }
        }
        return cpaId
    }

    fun deleteAll(): Int {
        return transaction(database.db) {
            CPA.deleteAll()
        }
    }

    fun cpaByHerId(herId: String): Map<Instant, CollaborationProtocolAgreement> {
        return transaction(database.db) {
            CPA.select(CPA.updated_date, CPA.cpa).where { CPA.herId.eq(herId) }.toList().groupBy({
                it[CPA.updated_date]
            }, {
                it[CPA.cpa]
            }).mapValues {
                it.value.first()
            }
        }
    }

    fun getProcessConfig(role: String, service: String, action: String): ProcessConfig? {
        return transaction(database.db) {
            ProcessConfigTable.selectAll().where {
                ProcessConfigTable.role.eq(role)
                ProcessConfigTable.service.eq(service)
                ProcessConfigTable.action.eq(action)
            }.firstOrNull()?.let {
                ProcessConfig(
                    it[ProcessConfigTable.kryptering],
                    it[ProcessConfigTable.komprimering],
                    it[ProcessConfigTable.signering],
                    it[ProcessConfigTable.internformat],
                    it[ProcessConfigTable.validering],
                    it[ProcessConfigTable.apprec],
                    it[ProcessConfigTable.adapter]
                )
            }
        }
    }

    data class CpaDbEntry(
        val id: String,
        val cpa: CollaborationProtocolAgreement? = null,
        val updatedDate: Instant, // OBS! Truncates til seconds av praktiske hensyn.
        val createdDate: Instant, // OBS! Truncates til seconds av praktiske hensyn.
        val herId: String?
    ) {
        constructor(cpa: CollaborationProtocolAgreement, updatedDate: Instant) : this(
            id = cpa.cpaid,
            cpa = cpa,
            updatedDate = updatedDate,
            createdDate = Instant.now(),
            herId = cpa.getPartnerPartyIdByType(PartyTypeEnum.HER)?.value
        )
    }

    // @Serializable
    // data class TimestampResponse(
    //    val idMap: Map<String, String>
    // )

    // fun List<Pair<>>.toTimestampResponse() {}
}
