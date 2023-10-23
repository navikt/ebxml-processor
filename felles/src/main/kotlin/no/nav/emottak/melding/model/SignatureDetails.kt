package no.nav.emottak.melding.model

data class SignatureDetails(
    val certificate: ByteArray,
    val signatureAlgorithm: String,
    val hashFunction: String
)


data class SignatureDetailsRequest(
    val cpaId: String,
    val partyType: String,
    val partyId: String,
    val role: String,
    val service: String,
    val action: String
)