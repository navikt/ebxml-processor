package no.nav.emottak.ebms.persistence

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object EbmsMessageTable : Table("ebms_message") {
    val cpaId: Column<String> = varchar("cpa_id", 256)
    val conversationId: Column<String> = varchar("conversation_id", 256)
    val messageId: Column<String> = varchar("message_id", 256)
    val refToMessageId: Column<String?> = varchar("ref_to_message_id", 256).nullable()
    val fromPartyId: Column<String> = varchar("from_party_id", 256)
    val fromRole: Column<String?> = varchar("from_role", 256).nullable()
    val toPartyId: Column<String> = varchar("to_party_id", 256)
    val toRole: Column<String?> = varchar("to_role", 256).nullable()
    val service: Column<String> = varchar("service", 256)
    val action: Column<String> = varchar("action", 256)
}
