package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.action
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.conversationId
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.cpaId
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.createdAt
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.fromPartyId
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.fromRole
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.messageId
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.refToMessageId
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.sentAt
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.service
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.toPartyId
import no.nav.emottak.ebms.async.persistence.table.EbmsMessageDetailsTable.toRole
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.EbmsMessageDetails
import no.nav.emottak.message.model.toEbmsMessageDetails
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory
import java.sql.SQLException
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class EbmsMessageDetailsRepository(private val database: Database) {

    private val log = LoggerFactory.getLogger(EbmsMessageDetailsRepository::class.java)

    val unsupportedServices = listOf(
        "HarBorgerFrikort",
        "HarBorgerEgenandelFritak"
    )

    private fun updateOrInsert(ebmsMessageDetails: EbmsMessageDetails): Uuid {
        transaction(database.db) {
            EbmsMessageDetailsTable.upsert(EbmsMessageDetailsTable.requestId) {
                it[requestId] = ebmsMessageDetails.requestId.toJavaUuid()
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
                it[sentAt] = ebmsMessageDetails.sentAt
            }
        }
        return ebmsMessageDetails.requestId
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
                        it[EbmsMessageDetailsTable.requestId].toKotlinUuid(),
                        it[EbmsMessageDetailsTable.cpaId],
                        it[EbmsMessageDetailsTable.conversationId],
                        it[EbmsMessageDetailsTable.messageId],
                        it[refToMessageId],
                        it[fromPartyId],
                        it[fromRole],
                        it[toPartyId],
                        it[toRole],
                        it[service],
                        it[action],
                        it[sentAt],
                        it[createdAt]
                    )
                }
        }
        return ebmsMessageDetails
    }

    fun getByRequestId(requestId: Uuid): EbmsMessageDetails? {
        var ebmsMessageDetails: EbmsMessageDetails? = null

        transaction(database.db) {
            EbmsMessageDetailsTable
                .select(EbmsMessageDetailsTable.columns)
                .where { EbmsMessageDetailsTable.requestId.eq(requestId.toJavaUuid()) }
                .firstOrNull()
                ?.also {
                    ebmsMessageDetails = EbmsMessageDetails(
                        it[EbmsMessageDetailsTable.requestId].toKotlinUuid(),
                        it[cpaId],
                        it[conversationId],
                        it[messageId],
                        it[refToMessageId],
                        it[fromPartyId],
                        it[fromRole],
                        it[toPartyId],
                        it[toRole],
                        it[service],
                        it[action],
                        it[sentAt],
                        it[createdAt]
                    )
                }
        }
        return ebmsMessageDetails
    }

    fun saveEbmsMessageDetails(messageDetails: EbmsMessageDetails): Uuid? {
        return if (!unsupportedServices.contains(messageDetails.service)) {
            updateOrInsert(messageDetails)
        } else {
            null
        }
    }

    fun saveEbmsMessage(
        ebmsMessage: EbmsMessage
    ) {
        val markers = ebmsMessage.marker()
        try {
            this.saveEbmsMessageDetails(ebmsMessage.toEbmsMessageDetails()).also {
                if (it == null) {
                    log.info(markers, "Message details has not been saved to database")
                } else {
                    log.info(markers, "Message details saved to database")
                }
            }
        } catch (ex: SQLException) {
            val hint = this.handleSQLException(ex)
            log.error(markers, "SQL exception ${ex.sqlState} occurred while saving message details to database: $hint", ex)
        } catch (ex: Exception) {
            log.error(markers, "Error occurred while saving message details to database", ex)
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
