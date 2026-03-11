package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.MessageReceivedTable
import no.nav.emottak.ebms.async.persistence.table.MessageReceivedTable.referenceId
import no.nav.emottak.ebms.async.util.getPreferredPartyId
import no.nav.emottak.message.model.PayloadMessage
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.UUID

class MessageReceivedRepository(private val database: Database) {

    fun updateOrInsert(ebmsPayloadMessage: PayloadMessage): String {
        transaction(database.db) {
            MessageReceivedTable.upsert(referenceId) {
                it[referenceId] = UUID.fromString(ebmsPayloadMessage.requestId)
                it[conversationId] = ebmsPayloadMessage.conversationId
                it[messageId] = ebmsPayloadMessage.messageId
                it[refToMessageId] = ebmsPayloadMessage.refToMessageId
                it[cpaId] = ebmsPayloadMessage.cpaId
                it[senderRole] = ebmsPayloadMessage.addressing.from.role
                it[senderId] = ebmsPayloadMessage.addressing.from.partyId.getPreferredPartyId().value
                it[receiverRole] = ebmsPayloadMessage.addressing.to.role
                it[receiverId] = ebmsPayloadMessage.addressing.to.partyId.getPreferredPartyId().value
                it[service] = ebmsPayloadMessage.addressing.service
                it[action] = ebmsPayloadMessage.addressing.action
                it[receivedAt] = java.time.Instant.now()
                it[ackSent] = true
            }
        }
        return ebmsPayloadMessage.requestId
    }

    fun getByReferenceId(referenceId: UUID): UUID? = transaction(database.db) {
        MessageReceivedTable
            .selectAll()
            .where {
                MessageReceivedTable.referenceId eq referenceId
            }
            .map { it[MessageReceivedTable.referenceId] }
            .firstOrNull()
    }

    fun isDuplicateMessage(ebmsPayloadMessage: PayloadMessage): Boolean = transaction(database.db) {
        MessageReceivedTable
            .selectAll()
            .where {
                (MessageReceivedTable.conversationId eq ebmsPayloadMessage.conversationId) and
                    (MessageReceivedTable.messageId eq ebmsPayloadMessage.messageId) and
                    (MessageReceivedTable.cpaId eq ebmsPayloadMessage.cpaId)
            }
            .any()
    }
}
