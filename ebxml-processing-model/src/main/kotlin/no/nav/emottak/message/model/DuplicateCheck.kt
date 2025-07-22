package no.nav.emottak.message.model

import kotlinx.serialization.Serializable

@Serializable
data class DuplicateCheckRequest(
    val requestId: String,
    val messageId: String,
    val conversationId: String,
    val cpaId: String
)

@Serializable
data class DuplicateCheckResponse(
    val requestId: String,
    val isDuplicate: Boolean
)
