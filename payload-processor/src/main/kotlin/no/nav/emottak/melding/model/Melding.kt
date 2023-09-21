package no.nav.emottak.melding.model

import kotlinx.serialization.Serializable
import no.nav.emottak.util.crypto.Dekryptering

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

val dekryptering = Dekryptering()

fun dekrypter(byteArray: ByteArray) = dekryptering.dekrypter(byteArray, false)
fun dekrypter(byteArray: ByteArray, isBase64: Boolean) = dekryptering.dekrypter(byteArray, isBase64)

fun Melding.dekrypter(isBase64: Boolean = false): Melding {
    return this.copy(
        processedPayload = dekryptering.dekrypter(this.processedPayload, isBase64),
        dekryptert = true
    )
}