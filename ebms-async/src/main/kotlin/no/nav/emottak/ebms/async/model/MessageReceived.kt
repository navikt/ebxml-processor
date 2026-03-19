package no.nav.emottak.ebms.async.model

import java.time.Instant
import kotlin.uuid.Uuid

data class MessageReceived(
    val referenceId: Uuid,
    val conversationId: String,
    val messageId: String,
    val refToMessageId: String?,
    val cpaId: String,
    val senderRole: String,
    val senderId: String,
    val receiverRole: String,
    val receiverId: String,
    val service: String,
    val action: String,
    val receivedAt: Instant,
    val acknowledged: Boolean
)
