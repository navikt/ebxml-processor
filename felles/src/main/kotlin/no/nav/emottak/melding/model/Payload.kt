package no.nav.emottak.melding.model

import kotlinx.serialization.Serializable


@Serializable
data class PayloadRequest(
    val header: Header,
    val payload: ByteArray
)

@Serializable
data class PayloadResponse(
    val processedPayload: ByteArray,
    val error: Error? = null
)

@Serializable
data class Error(val message:String)


@Serializable
data class Header(
    val messageId: String,
    val conversationId: String,
    val cpaId: String,
    val to: Party,
    val from: Party,
    val service: String,
    val action: String
)

@Serializable
data class Party(
    val herID: String,
    val role: String
)

@Serializable
data class ValidationResult(
    val valid: Boolean
)
