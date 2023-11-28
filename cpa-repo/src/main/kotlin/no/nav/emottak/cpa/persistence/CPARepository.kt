package no.nav.emottak.cpa.persistence

import no.nav.emottak.cpa.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class CPARepository(val database: Database) {

        fun findCpa(cpaId: String) : CollaborationProtocolAgreement? {
            return transaction(db = database.db) {
                 CPA.select(where = {
                    CPA.id.eq(cpaId)
                }).firstOrNull()?.get(CPA.cpa)
            }
        }

        fun putCpa(cpa: CollaborationProtocolAgreement): String {
            transaction(database.db) {
                CPA.insert {
                    it[CPA.id] = cpa.cpaid
                    it[CPA.cpa] = cpa
                }
            }
            return cpa.cpaid
        }
}