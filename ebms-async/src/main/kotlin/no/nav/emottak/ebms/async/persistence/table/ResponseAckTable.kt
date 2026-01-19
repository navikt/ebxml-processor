package no.nav.emottak.ebms.async.persistence.table

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant

object ResponseAckTable : Table("responseack") {
    val messageId: Column<String> = varchar("message_id", 256)
    val requestId: Column<String> = varchar("request_id", 256)
    val ackReceived: Column<Boolean> = bool("ack_received")
    val messageHeader: Column<String> = text("header")
    val messageContent: Column<ByteArray> = binary("content")
    val emailAddressList: Column<String> = varchar("email_list", 256)
    val firstSent: Column<Instant> = timestamp("first_sent_at")
    val lastSent: Column<Instant> = timestamp("last_sent_at")
    val resentCount: Column<Int> = integer("resent_count")
}
