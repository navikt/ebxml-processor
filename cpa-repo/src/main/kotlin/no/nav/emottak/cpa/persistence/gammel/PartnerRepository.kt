package no.nav.emottak.cpa.persistence.gammel

import no.nav.emottak.cpa.feil.MultiplePartnerException
import no.nav.emottak.cpa.feil.PartnerNotFoundException
import no.nav.emottak.cpa.persistence.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class PartnerRepository(val database: Database) {

    fun findPartners(cpaID: String): Long {
        return transaction(database.db) {
            val partners =
                PARTNER_CPA.selectAll().where { PARTNER_CPA.cpaId.eq(cpaID) }.toList().map { it[PARTNER_CPA.partnerId] }
                    .distinct()
            if (partners.size > 1) throw MultiplePartnerException("More than one partner for CPA $cpaID")
            if (partners.isEmpty()) throw PartnerNotFoundException("No partner for CPA $cpaID")
            partners.iterator().next().toLong()
        }
    }
}
