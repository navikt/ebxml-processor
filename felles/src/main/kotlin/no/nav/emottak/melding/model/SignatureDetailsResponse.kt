package no.nav.emottak.melding.model

data class SignatureDetailsResponse(
    val certificate: ByteArray,
    val signatureAlgorithm: String,
    val hashFunction: String
)
