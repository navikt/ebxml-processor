package no.nav.emottak.ebms.async.persistence.table

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant
import java.util.UUID

object PayloadTable : Table("payload") {
    val referenceId: Column<UUID> = uuid("reference_id")
    val contentId: Column<String> = varchar("content_id", 256)
    val contentType: Column<String> = varchar("content_type", 256)
    val content: Column<ByteArray> = binary("content")
    val contentAt: Column<Instant> = timestamp("created_at") //.defaultExpression(CurrentTimestamp())

    override val primaryKey = PrimaryKey(referenceId, contentId)
}
