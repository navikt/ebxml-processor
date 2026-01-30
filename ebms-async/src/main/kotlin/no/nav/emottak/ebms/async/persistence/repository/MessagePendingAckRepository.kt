package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.MessagePendingAckTable
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.xml.xmlMarshaller
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import java.time.Instant
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

// Entity representing an outgoing Message, whether it has been acknowledged, and any resend history
data class MessagePendingAck(
    // Message ID for message, will be the value of refToMessageId for the corresponding Acknowledgment
    val messageId: String,
    // ID used as key on Kafka topics
    val requestId: String,
    // Flag indicating whether an Ack has been received or not
    val ackReceived: Boolean = false,
    // Message contents (needed for resend): header, contents, address
    val messageHeader: MessageHeader,
    val messageContent: ByteArray,
    val emailAddressList: List<String> = emptyList(),
    // Keep track of (re)sending
    val firstSent: Instant = Instant.now(),
    val lastSent: Instant = Instant.now(),
    val resentCount: Int = 0
)

class MessagePendingAckRepository(
    private val database: Database,
    val resendIntervalMinutes: Int,
    val maxResends: Int
) {

    fun storeMessagePendingAck(id: Uuid, header: MessageHeader, content: ByteArray, receiverEmailAddress: List<EmailAddress>) {
        val addressListAsStringList: List<String> = receiverEmailAddress.map { a -> a.emailAddress }
        val addressesAsString = addressListAsStringList.joinToString(",")
        val now = Instant.now()
        transaction(database.db) {
            MessagePendingAckTable
                .insert {
                    it[messageId] = Uuid.parse(header.messageData.messageId).toJavaUuid()
                    it[requestId] = id.toJavaUuid()
                    it[ackReceived] = false
                    it[messageHeader] = xmlMarshaller.marshal(header)
                    it[messageContent] = content
                    it[emailAddressList] = addressesAsString
                    it[firstSent] = now
                    it[lastSent] = now
                    it[resentCount] = 0
                }
        }
    }

    // Set last resent = now, and increase reset-count for message with given message id
    fun markResent(message: MessagePendingAck) {
        transaction(database.db) {
            val messageIdAsUuid = Uuid.parse(message.messageId).toJavaUuid()
            MessagePendingAckTable
                .update(where = { MessagePendingAckTable.messageId.eq(messageIdAsUuid) }) {
                    it[lastSent] = Instant.now()
                    it[resentCount] = message.resentCount + 1
                }
        }
    }

    // Set ackReceived for message with given message id
    fun registerAckForMessage(messageId: String) {
        var messageIdAsUuid: UUID? = null
        try {
            // Kan få acks med annet enn UUID, så lenge gamle emottak er i live. Disse trenger vi ikke gjøre noe med.
            messageIdAsUuid = Uuid.parse(messageId).toJavaUuid()
        } catch (e: Exception) {
            return
        }
        transaction(database.db) {
            MessagePendingAckTable
                .update(where = { MessagePendingAckTable.messageId.eq(messageIdAsUuid) }) {
                    it[ackReceived] = true
                }
        }
    }

    // Messages that have not received Ack, not been given up (due to max resends), and was last sent before cutoff
    fun findMessagesToResend(cutoffTime: Instant? = null): List<MessagePendingAck> {
        var lastSentCutoff = Instant.now().minusSeconds((60 * resendIntervalMinutes).toLong())
        if (cutoffTime != null) {
            lastSentCutoff = cutoffTime
        }
        return transaction(database.db) {
            MessagePendingAckTable
                .select(MessagePendingAckTable.columns)
                .where {
                    MessagePendingAckTable.ackReceived.eq(false)
                        .and(MessagePendingAckTable.resentCount.less(maxResends))
                        .and(MessagePendingAckTable.lastSent.less(lastSentCutoff))
                }
                .map {
                    MessagePendingAck(
                        it[MessagePendingAckTable.messageId].toString(),
                        it[MessagePendingAckTable.requestId].toString(),
                        it[MessagePendingAckTable.ackReceived],
                        xmlMarshaller.unmarshal(it[MessagePendingAckTable.messageHeader], MessageHeader::class.java),
                        it[MessagePendingAckTable.messageContent],
                        it[MessagePendingAckTable.emailAddressList].split(","),
                        it[MessagePendingAckTable.firstSent],
                        it[MessagePendingAckTable.lastSent],
                        it[MessagePendingAckTable.resentCount]
                    )
                }
        }
    }
}
