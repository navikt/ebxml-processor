package no.nav.emottak.cpa.persistence

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object ProcessConfigTable : Table("process_config") {
    val role: Column<String> = varchar("role", 50)
    val service: Column<String> = varchar("service", 50)
    val action: Column<String> = varchar("action", 50)
    val kryptering: Column<Boolean> = bool("kryptering")
    val komprimering: Column<Boolean> = bool("komprimering")
    val signering: Column<Boolean> = bool("signering")
    val internformat: Column<Boolean> = bool("internformat")
    val validering: Column<Boolean> = bool("validering")
    val ocspCheck: Column<Boolean> = bool("ocsp_check")
    val adapter: Column<String?> = varchar("adapter", 50).nullable()
    val apprec: Column<Boolean> = bool("apprec")
    val errorAction: Column<String?> = varchar("error_action", 50).nullable()
    val pk = PrimaryKey(role, service, action)
}
