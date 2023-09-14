package no.nav.emottak.melding.model

import kotlinx.serialization.Serializable

@Serializable
data class Melding(
    val header: Header,
    val originalPayload: ByteArray,
    val processedPayload: ByteArray
)

@Serializable
data class Header(
    val messageId: String,
    val conversationId: String,
    val to: Part,
    val from: Part,
    val role: String,
    val service: String,
    val action: String
)

@Serializable
data class Part(
    val krypteringSertifikat: ByteArray,
    val signeringSertifikat: ByteArray,
    val herID: String
)
