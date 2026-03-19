package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.model.MessageReceived
import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.MessageReceivedTable
import no.nav.emottak.ebms.async.util.getPreferredPartyId
import no.nav.emottak.message.model.PayloadMessage
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class MessageReceivedRepository(private val database: Database) {

    fun messageReceived(ebmsPayloadMessage: PayloadMessage) = updateOrInsert(ebmsPayloadMessage, false)

    fun messageAcknowledged(ebmsPayloadMessage: PayloadMessage) = updateOrInsert(ebmsPayloadMessage, true)

    private fun updateOrInsert(ebmsPayloadMessage: PayloadMessage, acknowledged: Boolean): String {
        transaction(database.db) {
            MessageReceivedTable.upsert(MessageReceivedTable.referenceId) {
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
                it[this.acknowledged] = acknowledged
            }
        }
        return ebmsPayloadMessage.requestId
    }

    fun getByReferenceId(referenceId: Uuid): MessageReceived? = transaction(database.db) {
        MessageReceivedTable
            .selectAll()
            .where {
                MessageReceivedTable.referenceId eq referenceId.toJavaUuid()
            }
            .map { it.toMessageReceived() }
            .firstOrNull()
    }

    fun getMessageReceived(ebmsPayloadMessage: PayloadMessage): MessageReceived? = transaction(database.db) {
        MessageReceivedTable
            .selectAll()
            .where {
                (MessageReceivedTable.conversationId eq ebmsPayloadMessage.conversationId) and
                    (MessageReceivedTable.messageId eq ebmsPayloadMessage.messageId) and
                    (MessageReceivedTable.cpaId eq ebmsPayloadMessage.cpaId)
            }
            .map { it.toMessageReceived() }
            .firstOrNull()
    }

    private fun ResultRow.toMessageReceived() = MessageReceived(
        referenceId = this[MessageReceivedTable.referenceId].toKotlinUuid(),
        conversationId = this[MessageReceivedTable.conversationId],
        messageId = this[MessageReceivedTable.messageId],
        refToMessageId = this[MessageReceivedTable.refToMessageId],
        cpaId = this[MessageReceivedTable.cpaId],
        senderRole = this[MessageReceivedTable.senderRole],
        senderId = this[MessageReceivedTable.senderId],
        receiverRole = this[MessageReceivedTable.receiverRole],
        receiverId = this[MessageReceivedTable.receiverId],
        service = this[MessageReceivedTable.service],
        action = this[MessageReceivedTable.action],
        receivedAt = this[MessageReceivedTable.receivedAt],
        acknowledged = this[MessageReceivedTable.acknowledged]
    )
}
