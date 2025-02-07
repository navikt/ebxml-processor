package no.nav.emottak.ebms.persistence.repository

import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.table.PayloadTable
import no.nav.emottak.ebms.persistence.table.PayloadTable.contentId
import no.nav.emottak.ebms.persistence.table.PayloadTable.referenceId
import no.nav.emottak.message.model.AsyncPayload
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.util.UUID

class PayloadRepository(private val database: Database) {

    fun updateOrInsert(payload: AsyncPayload): UUID {
        transaction(database.db) {
            PayloadTable.upsert(referenceId, contentId) {
                it[referenceId] = payload.referenceId.toString()
                it[contentId] = payload.contentId
                it[contentType] = payload.contentType
                it[content] = payload.content
            }
        }
        return payload.referenceId
    }

    fun getByReferenceId(referenceId: UUID): List<AsyncPayload> {
        return transaction(database.db) {
            PayloadTable
                .select(PayloadTable.columns)
                .where { PayloadTable.referenceId.eq(referenceId.toString()) }
                .map {
                    AsyncPayload(
                        UUID.fromString(it[PayloadTable.referenceId]),
                        it[PayloadTable.contentId],
                        it[PayloadTable.contentType],
                        it[PayloadTable.content]
                    )
                }
        }
    }
}
