package no.nav.emottak.cpa.model

import kotlinx.serialization.Serializable


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