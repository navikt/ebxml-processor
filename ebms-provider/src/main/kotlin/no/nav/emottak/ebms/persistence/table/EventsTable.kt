package no.nav.emottak.ebms.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

// Bruk av kotlin.kotlin.Uuid i Column støttes _kanskje_ i kotlin 2.1.20 eller nyere (+ KTOR v3)
// Se https://youtrack.jetbrains.com/issue/EXPOSED-507
object EventsTable : Table("events") {
    val eventId: Column<UUID> = uuid("event_id")
    val requestId: Column<UUID> = uuid("request_id").references(EbmsMessageDetailsTable.requestId)
    val contentId: Column<String?> = varchar("content_id", 256).nullable()
    val messageId: Column<String> = varchar("message_id", 256)
    val juridiskLoggId: Column<String?> = varchar("juridisk_logg_id", 256).nullable()
    val eventMessage: Column<String> = varchar("event_message", 256)
    val createdAt: Column<Instant> = timestamp("created_at").defaultExpression(CurrentTimestamp())

    override val primaryKey = PrimaryKey(eventId)
}
