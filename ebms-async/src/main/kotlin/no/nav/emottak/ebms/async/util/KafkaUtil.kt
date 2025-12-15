package no.nav.emottak.ebms.async.util

import no.nav.emottak.message.model.EmailAddress
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

private const val EBXML_RECEIVER_ADDRESS = "emailAddresses"
private const val EBXML_SENDER_ADDRESS = "ebxmlSenderAddress"
private const val EBXML_SERVICE_HEADER = "ebxmlService"
private const val EBXML_ACTION_HEADER = "ebxmlAction"
private const val EBXML_CPA_ID_HEADER = "ebxmlCpaId"
private const val EBXML_CONVERSATION_ID_HEADER = "ebxmlConversationId"
private const val EBXML_MESSAGE_ID_HEADER = "ebxmlMessageId"
private const val EBXML_REF_TO_MESSAGE_ID_HEADER = "ebxmlRefToMessageId"

fun List<EmailAddress>.receiverAddressToKafkaHeader(): List<Header> = listOf(
    RecordHeader(
        EBXML_RECEIVER_ADDRESS,
        joinToString(",") { it.emailAddress }.toByteArray()
    )
)

fun List<EmailAddress>.senderAddressToKafkaHeader(): List<Header> = listOf(
    RecordHeader(
        EBXML_SENDER_ADDRESS,
        joinToString(",") { it.emailAddress }.toByteArray()
    )
)

fun MessageHeader.toKafkaHeaders(): List<Header> =
    mapOf(
        EBXML_SERVICE_HEADER to this.service.value,
        EBXML_ACTION_HEADER to this.action,
        EBXML_CPA_ID_HEADER to this.cpaId,
        EBXML_CONVERSATION_ID_HEADER to this.conversationId,
        EBXML_MESSAGE_ID_HEADER to this.messageData.messageId,
        EBXML_REF_TO_MESSAGE_ID_HEADER to this.messageData.refToMessageId
    ).toKafkaHeaders()

fun Map<String, String?>.toKafkaHeaders(): List<Header> =
    this.map { (key, value) ->
        RecordHeader(
            key,
            value.orEmpty().toByteArray()
        )
    }
