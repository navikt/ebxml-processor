package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.PayloadTable
import no.nav.emottak.ebms.async.persistence.table.PayloadTable.contentId
import no.nav.emottak.ebms.async.persistence.table.PayloadTable.referenceId
import no.nav.emottak.message.model.AsyncPayload
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class PayloadRepository(private val database: Database) {

    fun updateOrInsert(payload: AsyncPayload): Uuid {
        transaction(database.db) {
            PayloadTable.upsert(referenceId, contentId) {
                it[referenceId] = payload.referenceId.toJavaUuid()
                it[contentId] = payload.contentId
                it[contentType] = payload.contentType
                it[content] = payload.content
            }
        }
        return payload.referenceId
    }

    fun getByReferenceId(referenceId: Uuid): List<AsyncPayload> {
        return transaction(database.db) {
            PayloadTable
                .select(PayloadTable.columns)
                .where { PayloadTable.referenceId.eq(referenceId.toJavaUuid()) }
                .map {
                    AsyncPayload(
                        it[PayloadTable.referenceId].toKotlinUuid(),
                        it[PayloadTable.contentId],
                        it[PayloadTable.contentType],
                        it[PayloadTable.content]
                    )
                }
        }
    }
}
