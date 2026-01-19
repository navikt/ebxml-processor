package no.nav.emottak.ebms.async.persistence.repository

import no.nav.emottak.ebms.async.persistence.Database
import no.nav.emottak.ebms.async.persistence.table.ResponseAckTable
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

// Entity representing a Response Message, whether it has been acknowledged, and any resend history
data class ResponseMessageNeedingAck(
    // Message ID for response message, will be the value of refToMessageId for the corresponding Acknowledgment
    val messageId: String,
    // ID used as key on Kafka topics
    val requestId: String,
    // Flag indicating whether an Ack has been received or not
    val ackReceived: Boolean = false,
    // Response message contents (needed for resend): header, contents, address
    val messageHeader: MessageHeader,
    val messageContent: ByteArray,
    val emailAddressList: List<String> = emptyList(),
    // Keep track of (re)sending
    val firstSent: Instant = Instant.now(),
    val lastSent: Instant = Instant.now(),
    val resentCount: Int = 0
)

class ResponseAckRepository(
    private val database: Database,
    val resendIntervalMinutes: Int,
    val maxResends: Int
) {

    fun storeResponse(id: String, header: MessageHeader, content: ByteArray, receiverEmailAddress: List<EmailAddress>) {
        val addressListAsStringList: List<String> = receiverEmailAddress.map { a -> a.emailAddress }
        val addressesAsString = addressListAsStringList.joinToString(",")
        val now = Instant.now()
        transaction(database.db) {
            ResponseAckTable
                .insert {
                    it[messageId] = header.messageData.messageId
                    it[requestId] = id
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

    // Set last resent = now, and increase reset-count for response with given message id
    fun markResent(response: ResponseMessageNeedingAck) {
        transaction(database.db) {
            ResponseAckTable
                .update(where = { ResponseAckTable.messageId.eq(response.messageId) }) {
                    it[lastSent] = Instant.now()
                    it[resentCount] = response.resentCount + 1
                }
        }
    }

    // Set ackReceived for response with given message id
    fun registerAckForMessage(messageId: String) {
        transaction(database.db) {
            ResponseAckTable
                .update(where = { ResponseAckTable.messageId.eq(messageId) }) {
                    it[ackReceived] = true
                }
        }
    }

    // Responses that have not received Ack, not been given up (due to max resends), and was last sent before cutoff
    fun findResponsesToResend(cutoffTime: Instant? = null): List<ResponseMessageNeedingAck> {
        var lastSentCutoff = Instant.now().minusSeconds((60 * resendIntervalMinutes).toLong())
        if (cutoffTime != null) {
            lastSentCutoff = cutoffTime
        }
        return transaction(database.db) {
            ResponseAckTable
                .select(ResponseAckTable.columns)
                .where {
                    ResponseAckTable.ackReceived.eq(false)
                        .and(ResponseAckTable.resentCount.less(maxResends))
                        .and(ResponseAckTable.lastSent.less(lastSentCutoff))
                }
                .map {
                    ResponseMessageNeedingAck(
                        it[ResponseAckTable.messageId],
                        it[ResponseAckTable.requestId],
                        it[ResponseAckTable.ackReceived],
                        xmlMarshaller.unmarshal(it[ResponseAckTable.messageHeader], MessageHeader::class.java),
                        it[ResponseAckTable.messageContent],
                        it[ResponseAckTable.emailAddressList].split(","),
                        it[ResponseAckTable.firstSent],
                        it[ResponseAckTable.lastSent],
                        it[ResponseAckTable.resentCount]
                    )
                }
        }
    }
}
