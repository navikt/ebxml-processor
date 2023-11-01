package no.nav.emottak.melding.model

import kotlinx.serialization.Serializable

@Serializable
data class SignatureDetails(
    val certificate: ByteArray,
    val signatureAlgorithm: String,
    val hashFunction: String
)

@Serializable
data class SignatureDetailsRequest(
    val cpaId: String,
    val partyType: String,
    val partyId: String,
    val role: String,
    val service: String,
    val action: String
)
