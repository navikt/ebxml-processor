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
) {
    constructor(payloadRequest: PayloadRequest) : this(
        header = payloadRequest.header,
        originalPayload = payloadRequest.payload,
        processedPayload = payloadRequest.payload)
}