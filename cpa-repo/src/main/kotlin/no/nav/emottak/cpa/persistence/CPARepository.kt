package no.nav.emottak.cpa.persistence

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.CollaborationProtocolAgreement

class CPARepository {

        fun findCpa(cpaId: String) : CollaborationProtocolAgreement? {
            return transaction {
                 CPA.select(where = {
                    CPA.id.eq(cpaId)
                }).firstOrNull()?.get(CPA.cpa)
            }
        }

        fun putCpa(cpa: CollaborationProtocolAgreement) {
            transaction {
                CPA.insert {
                    it[CPA.id] = cpa.cpaid
                    it[CPA.cpa] = cpa
                }
            }
        }
}