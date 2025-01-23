package no.nav.emottak.ebms.persistence.repository

import no.nav.emottak.ebms.persistence.Database
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.action
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.conversationId
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.cpaId
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.fromPartyId
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.fromRole
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.messageId
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.refToMessageId
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.service
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.toPartyId
import no.nav.emottak.ebms.persistence.table.EbmsMessageDetailsTable.toRole
import no.nav.emottak.message.model.EbmsMessageDetails
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.sql.SQLException
import java.util.UUID

class EbmsMessageDetailsRepository(private val database: Database) {

    val unsupportedServices = listOf(
        "HarBorgerFrikort",
        "HarBorgerEgenandelFritak"
    )

    private fun updateOrInsert(ebmsMessageDetails: EbmsMessageDetails): UUID {
        transaction(database.db) {
            EbmsMessageDetailsTable.upsert(EbmsMessageDetailsTable.referenceId) {
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

    fun getByConversationIdMessageIdAndCpaId(conversationId: String, messageId: String, cpaId: String): EbmsMessageDetails? {
        var ebmsMessageDetails: EbmsMessageDetails? = null

        transaction(database.db) {
            EbmsMessageDetailsTable
                .select(EbmsMessageDetailsTable.columns)
                .where {
                    (EbmsMessageDetailsTable.conversationId eq conversationId) and
                        (EbmsMessageDetailsTable.messageId eq messageId) and
                        (EbmsMessageDetailsTable.cpaId eq cpaId)
                }
                .firstOrNull()
                ?.also {
                    ebmsMessageDetails = EbmsMessageDetails(
                        it[EbmsMessageDetailsTable.referenceId],
                        it[EbmsMessageDetailsTable.cpaId],
                        it[EbmsMessageDetailsTable.conversationId],
                        it[EbmsMessageDetailsTable.messageId],
                        it[refToMessageId],
                        it[fromPartyId],
                        it[fromRole],
                        it[toPartyId],
                        it[toRole],
                        it[service],
                        it[action]
                    )
                }
        }
        return ebmsMessageDetails
    }

    fun getByReferenceId(referenceId: UUID): EbmsMessageDetails? {
        var ebmsMessageDetails: EbmsMessageDetails? = null

        transaction(database.db) {
            EbmsMessageDetailsTable
                .select(EbmsMessageDetailsTable.columns)
                .where { EbmsMessageDetailsTable.referenceId.eq(referenceId) }
                .firstOrNull()
                ?.also {
                    ebmsMessageDetails = EbmsMessageDetails(
                        it[EbmsMessageDetailsTable.referenceId],
                        it[cpaId],
                        it[conversationId],
                        it[messageId],
                        it[refToMessageId],
                        it[fromPartyId],
                        it[fromRole],
                        it[toPartyId],
                        it[toRole],
                        it[service],
                        it[action]
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
