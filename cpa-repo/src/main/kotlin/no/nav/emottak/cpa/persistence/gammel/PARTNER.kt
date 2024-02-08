package no.nav.emottak.cpa.persistence.gammel

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.RuntimeException

object PARTNER : Table("PARTNER") {
    val partnerId = ulong("PARTNER_ID")
    val herId = varchar("HER_ID", 50)
}

object PARTNER_CPA : Table("PARTNER_CPA") {
    val cpaId = varchar("CPA_ID", 50)
    val partnerId = ulong("PARTNER_ID")
}

fun findPartners(database: Database, cpaID: String): Long? {
    return transaction(database) {
        val partners = PARTNER_CPA.select(PARTNER_CPA.cpaId.eq(cpaID)).toList().map { it.get(PARTNER_CPA.partnerId) }.distinct()
        if (partners.size > 1) throw RuntimeException("More than one partner")
        partners.iterator().next()?.toLong()
    }
}
