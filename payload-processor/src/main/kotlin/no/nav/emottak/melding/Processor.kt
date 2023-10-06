package no.nav.emottak.melding

import no.nav.emottak.melding.model.Melding
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.util.GZipUtil
import no.nav.emottak.util.signatur.SignaturVerifisering
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.crypto.Dekryptering
import no.nav.emottak.util.crypto.Kryptering
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.hentKrypteringssertifikat
import no.nav.emottak.util.signatur.Signering
import java.io.ByteArrayInputStream

class Processor {

    fun process(payloadRequest: PayloadRequest): PayloadResponse {
        return if (payloadRequest.isIncomingMessage()) {
            processIncoming(payloadRequest)
        } else {
            processOutgoing(payloadRequest)
        }
    }

    private fun processIncoming(payloadRequest: PayloadRequest): PayloadResponse {
        val melding = Melding(payloadRequest)
            .dekrypter()
            .dekomprimer()
            //.verifiserXML()
            .verifiserSignatur()
        return PayloadResponse(
            melding.processedPayload
        )
    }

    fun processOutgoing(payloadRequest: PayloadRequest): PayloadResponse {
        val melding = Melding(payloadRequest)
            //.verifiserXML()
            .signer()
            .komprimer()
            .krypter()
        return PayloadResponse(
            melding.processedPayload
        )
    }
}

fun PayloadRequest.isIncomingMessage(): Boolean {
    //TODO
    return true
}

private val kryptering = Kryptering()
private val dekryptering = Dekryptering()
private val signering = Signering()
private val gZipUtil = GZipUtil()
private val signatureVerifisering = SignaturVerifisering()

fun Melding.dekrypter(isBase64: Boolean = false): Melding {
    return this.copy(
        processedPayload = dekryptering.dekrypter(this.processedPayload, isBase64),
        dekryptert = true
    )
}

fun Melding.signer(): Melding {
    return this.copy(
        processedPayload = getByteArrayFromDocument(
            signering.signerXML(createDocument( ByteArrayInputStream(this.processedPayload)))
        ),
        signert = true
    )
}

fun Melding.dekomprimer(): Melding {
    return this.copy(
        processedPayload = gZipUtil.uncompress(this.processedPayload),
        dekomprimert = true
    )
}

fun Melding.komprimer(): Melding {
    return this.copy(
        processedPayload = gZipUtil.compress(this.processedPayload),
        komprimert = true
    )
}

fun Melding.verifiserSignatur(): Melding {
    signatureVerifisering.validate(this.processedPayload)
    return this.copy(
        signaturVerifisert = true
    )
}

fun Melding.krypter(): Melding {
    val krypteringSertifikat = hentKrypteringssertifikat(header.cpaId, header.to.herID)
    return this.copy(
        processedPayload = kryptering.krypter(this.processedPayload, krypteringSertifikat),
        kryptert = true
    )
}

