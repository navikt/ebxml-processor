package no.nav.emottak.ebms.persistence.repository

import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.table.PayloadTable
import no.nav.emottak.ebms.persistence.table.PayloadTable.contentId
import no.nav.emottak.ebms.persistence.table.PayloadTable.referenceId
import no.nav.emottak.message.model.AsyncPayload
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PayloadRepository(private val database: Database) {

    @OptIn(ExperimentalUuidApi::class)
    fun updateOrInsert(payload: AsyncPayload): Uuid {
        transaction(database.db) {
            PayloadTable.upsert(referenceId, contentId) {
                it[referenceId] = payload.referenceId
                it[contentId] = payload.contentId
                it[contentType] = payload.contentType
                it[content] = payload.content
            }
        }
        return Uuid.parse(payload.referenceId)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getByReferenceId(referenceId: Uuid): List<AsyncPayload> {
        return transaction(database.db) {
            PayloadTable
                .select(PayloadTable.columns)
                .where { PayloadTable.referenceId.eq(referenceId.toString()) }
                .map {
                    AsyncPayload(
                        it[PayloadTable.referenceId],
                        it[PayloadTable.contentId],
                        it[PayloadTable.contentType],
                        it[PayloadTable.content]
                    )
                }
        }
    }
}
