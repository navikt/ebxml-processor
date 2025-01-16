package no.nav.emottak.ebms.persistence

import no.nav.emottak.ebms.persistence.EbmsMessageTable.action
import no.nav.emottak.ebms.persistence.EbmsMessageTable.conversationId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.cpaId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.fromPartyId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.fromRole
import no.nav.emottak.ebms.persistence.EbmsMessageTable.messageId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.refToMessageId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.service
import no.nav.emottak.ebms.persistence.EbmsMessageTable.toPartyId
import no.nav.emottak.ebms.persistence.EbmsMessageTable.toRole
import no.nav.emottak.message.model.EbmsMessageDetails
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.sql.SQLException
import java.util.UUID

class EbmsMessageRepository(private val database: Database) {

    val unsupportedServices = listOf(
        "HarBorgerFrikort",
        "HarBorgerEgenandelFritak"
    )

    private fun updateOrInsert(ebmsMessageDetails: EbmsMessageDetails): UUID {
        transaction(database.db) {
            EbmsMessageTable.upsert(EbmsMessageTable.referenceId) {
                it[referenceId] = ebmsMessageDetails.referenceId
                it[cpaId] = ebmsMessageDetails.cpaId
                it[conversationId] = ebmsMessageDetails.conversationId
                it[messageId] = ebmsMessageDetails.messageId
                it[refToMessageId] = ebmsMessageDetails.refToMessageId
                it[fromPartyId] = ebmsMessageDetails.fromPartyId
                it[fromRole] = ebmsMessageDetails.fromRole
                it[toPartyId] = ebmsMessageDetails.toPartyId
                it[toRole] = ebmsMessageDetails.toRole
                it[service] = ebmsMessageDetails.service
                it[action] = ebmsMessageDetails.action
            }
        }
        return ebmsMessageDetails.referenceId
    }

    fun getByReferenceId(referenceId: UUID): EbmsMessageDetails? {
        var ebmsMessageDetails: EbmsMessageDetails? = null

        transaction(database.db) {
            EbmsMessageTable
                .select(EbmsMessageTable.columns)
                .where { EbmsMessageTable.referenceId.eq(referenceId) }
                .firstOrNull()
                ?.also {
                    ebmsMessageDetails = EbmsMessageDetails(
                        it[EbmsMessageTable.referenceId],
                        it[EbmsMessageTable.cpaId],
                        it[EbmsMessageTable.conversationId],
                        it[EbmsMessageTable.messageId],
                        it[EbmsMessageTable.refToMessageId],
                        it[EbmsMessageTable.fromPartyId],
                        it[EbmsMessageTable.fromRole],
                        it[EbmsMessageTable.toPartyId],
                        it[EbmsMessageTable.toRole],
                        it[EbmsMessageTable.service],
                        it[EbmsMessageTable.action]
                    )
                }
        }
        return ebmsMessageDetails
    }

    fun saveEbmsMessageDetails(messageDetails: EbmsMessageDetails): UUID? {
        return if (!unsupportedServices.contains(messageDetails.service)) {
            updateOrInsert(messageDetails)
        } else {
            null
        }
    }

    fun handleSQLException(exception: SQLException): String {
        return when (exception.sqlState) {
            "08001" -> "Connection failure: Verify the database URL, network connectivity, and credentials."
            "42000" -> "SQL Syntax Error: Check your query syntax."
            "23000" -> "Integrity Constraint Violation: Check primary keys, foreign keys, or unique constraints."
            else -> "Unhandled SQL State. Please check the error code and message for more details."
        }
    }
}
