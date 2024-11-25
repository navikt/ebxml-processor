package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.getPartnerPartyIdByType
import no.nav.emottak.cpa.log
import no.nav.emottak.cpa.xmlMarshaller
import no.nav.emottak.message.ebxml.PartyTypeEnum
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.util.isProdEnv
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement
import java.time.Instant
import java.time.temporal.ChronoUnit

class CPARepository(val database: Database) {

    fun findCpa(cpaId: String): CollaborationProtocolAgreement? {
        if (cpaId == "nav:qass:30823" && !isProdEnv()) {
            return loadOverrideCPA()
        }
        return transaction(db = database.db) {
            CPA.selectAll().where {
                CPA.id.eq(cpaId)
            }.firstOrNull()?.get(
                CPA.cpa
            )
        }
    }

    fun loadOverrideCPA(): CollaborationProtocolAgreement {
        val cpaString = String(object {}::class.java.classLoader.getResource("cpa/nav_qass_30823_modified.xml").readBytes())
        return xmlMarshaller.unmarshal(cpaString, CollaborationProtocolAgreement::class.java)
    }

    fun findCpaTimestamps(idList: List<String>): Map<String, String> {
        return transaction(db = database.db) {
            if (idList.isNotEmpty()) {
                CPA.select(CPA.id, CPA.updated_date).where { CPA.id inList idList }
            } else {
                CPA.select(CPA.id, CPA.updated_date)
            }.associate {
                it[CPA.id] to it[CPA.updated_date].toString()
            }
        }
    }

    fun findLatestUpdatedCpaTimestamp(): String? {
        return transaction(db = database.db) {
            CPA.select(CPA.id, CPA.updated_date)
                .where { CPA.updated_date.isNotNull() }
                .orderBy(CPA.updated_date, SortOrder.DESC)
                .firstOrNull()?.get(CPA.updated_date)
        }?.toString()
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

    fun updateOrInsert(cpa: CpaDbEntry): String {
        transaction(database.db) {
            CPA.upsert(CPA.id) {
                it[id] = cpa.id
                it[CPA.cpa] = cpa.cpa ?: throw IllegalArgumentException("Kan ikke sette null verdi for CPA i DB")
                it[entryCreated] = cpa.createdDate
                it[updated_date] = cpa.updatedDate
                it[herId] = cpa.herId
            }
        }
        return cpa.id
    }

    fun deleteCpa(cpaId: String): String {
        transaction(database.db) {
            CPA.deleteWhere { id.eq(cpaId) }
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
                (ProcessConfigTable.role eq role) and
                    (ProcessConfigTable.service eq service) and
                    (ProcessConfigTable.action eq action)
            }.firstOrNull()?.let {
                ProcessConfig(
                    it[ProcessConfigTable.kryptering],
                    it[ProcessConfigTable.komprimering],
                    it[ProcessConfigTable.signering],
                    it[ProcessConfigTable.internformat],
                    it[ProcessConfigTable.validering],
                    it[ProcessConfigTable.apprec],
                    it[ProcessConfigTable.ocspCheck],
                    it[ProcessConfigTable.juridiskLogg],
                    it[ProcessConfigTable.adapter],
                    it[ProcessConfigTable.errorAction]
                )
            }.also {
                if (it == null) {
                    log.warn("Missing process config for $role, $service, $action")
                } else {
                    log.debug("Found process config for {}, {}, {}: {}", role, service, action, it)
                }
            }
        }
    }

    data class CpaDbEntry(
        val id: String,
        val cpa: CollaborationProtocolAgreement? = null,
        val updatedDate: Instant,
        val createdDate: Instant,
        val herId: String?
    ) {
        constructor(cpa: CollaborationProtocolAgreement, updatedDateString: String?) : this(
            id = cpa.cpaid,
            cpa = cpa,
            updatedDate = parseOrDefault(updatedDateString),
            createdDate = Instant.now().truncatedTo(ChronoUnit.SECONDS),
            herId = cpa.getPartnerPartyIdByType(PartyTypeEnum.HER)?.value
        )

        companion object {
            private fun parseOrDefault(dateString: String?): Instant {
                return if (dateString.isNullOrBlank()) {
                    Instant.now().truncatedTo(ChronoUnit.SECONDS)
                } else {
                    Instant.parse(dateString).truncatedTo(ChronoUnit.SECONDS)
                }
            }
        }
    }

    // @Serializable
    // data class TimestampResponse(
    //    val idMap: Map<String, String>
    // )

    // fun List<Pair<>>.toTimestampResponse() {}
}
