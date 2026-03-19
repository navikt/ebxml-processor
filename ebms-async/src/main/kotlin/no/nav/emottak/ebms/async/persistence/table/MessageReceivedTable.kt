package no.nav.emottak.ebms.async.persistence.table

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import java.util.UUID

object MessageReceivedTable : Table("message_received") {
    val referenceId: Column<UUID> = uuid("request_id")
    val conversationId: Column<String> = varchar("conversation_id", 256)
    val messageId: Column<String> = varchar("message_id", 256)
    val refToMessageId: Column<String?> = varchar("ref_to_message_id", 256).nullable()
    val cpaId: Column<String> = varchar("cpa_id", 256)
    val senderRole: Column<String> = varchar("sender_role", 256)
    val senderId: Column<String> = varchar("sender_id", 256)
    val receiverRole: Column<String> = varchar("receiver_role", 256)
    val receiverId: Column<String> = varchar("receiver_id", 256)
    val service: Column<String> = varchar("service", 256)
    val action: Column<String> = varchar("action", 256)
    val receivedAt: Column<java.time.Instant> = timestamp("received_at")
    val acknowledged: Column<Boolean> = bool("acknowledged")

    override val primaryKey = PrimaryKey(referenceId)
}
