package no.nav.emottak.cpa.persistence.gammel

import org.jetbrains.exposed.sql.Table

object PARTNER : Table("PARTNER") {
    val partnerId = ulong("PARTNER_ID")
    val herId = varchar("HER_ID", 50)
}

object PARTNER_CPA : Table("PARTNER_CPA") {
    val cpaId = varchar("CPA_ID", 50)
    val partnerId = ulong("PARTNER_ID")
}
