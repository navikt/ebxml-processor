package no.nav.emottak.ebms.async.persistence.table

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import java.util.UUID

object MessageReceivedTable : Table("message_received") {
    val referenceId: Column<UUID> = uuid("request_id")
    val conversationId: Column<String> = varchar("conversation_id", 256)
    val messageId: Column<String> = varchar("message_id", 256)
    val cpaId: Column<String> = varchar("cpa_id", 256)
    val receivedAt: Column<java.time.Instant> = timestamp("received_at")
    val ackSent: Column<Boolean> = bool("ack_sent")

    override val primaryKey = PrimaryKey(referenceId)
}
