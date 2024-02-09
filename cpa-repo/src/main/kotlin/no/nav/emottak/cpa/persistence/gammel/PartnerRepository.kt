package no.nav.emottak.cpa.persistence.gammel

import no.nav.emottak.cpa.persistence.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.RuntimeException

class PartnerRepository(val database: Database) {

    fun findPartners(cpaID: String): Long? {
        return transaction(database.db) {
            val partners =
                PARTNER_CPA.select(PARTNER_CPA.cpaId.eq(cpaID)).toList().map { it.get(PARTNER_CPA.partnerId) }
                    .distinct()
            if (partners.size > 1) throw RuntimeException("More than one partner")
            partners.iterator().next()?.toLong()
        }
    }
}
