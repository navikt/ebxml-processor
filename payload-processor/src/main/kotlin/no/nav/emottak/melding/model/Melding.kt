package no.nav.emottak.melding.model

import kotlinx.serialization.Serializable

@Serializable
data class Melding(
    val header: Header,
    val originalPayload: ByteArray,
    val processedPayload: ByteArray,
    val kryptert: Boolean = false,
    val dekryptert: Boolean = false,
    val signert: Boolean = false,
    val signaturVerifisert: Boolean = false,
    val sertifikatSjekket: Boolean = false,
    val komprimert: Boolean = false,
    val dekomprimert: Boolean = false
)

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
