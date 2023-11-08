package no.nav.emottak.melding

import no.nav.emottak.melding.model.Melding
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.util.GZipUtil
import no.nav.emottak.util.signatur.SignaturVerifisering
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.crypto.Dekryptering
import no.nav.emottak.util.crypto.Kryptering
import no.nav.emottak.util.crypto.erGyldig
import no.nav.emottak.util.crypto.krypterDokument
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.hentKrypteringssertifikat
import no.nav.emottak.util.marker
import no.nav.emottak.util.signatur.Signering
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

internal val log = LoggerFactory.getLogger("no.nav.emottak.melding.Processor")
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
            payloadRequest.payloadId,
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
            payloadRequest.payloadId,
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
    log.info(this.header.marker(), "Dekrypterer melding")
    return this.copy(
        processedPayload = dekryptering.dekrypter(this.processedPayload, isBase64),
        dekryptert = true
    )
}

fun Melding.signer(): Melding {
    log.info(this.header.marker(), "Signerer melding")
    return this.copy(
        processedPayload = getByteArrayFromDocument(
            signering.signerXML(createDocument( ByteArrayInputStream(this.processedPayload)))
        ),
        signert = true
    )
}

fun Melding.dekomprimer(): Melding {
    log.info(this.header.marker(), "Dekomprimerer melding")
    return this.copy(
        processedPayload = gZipUtil.uncompress(this.processedPayload),
        dekomprimert = true
    )
}

fun Melding.komprimer(): Melding {
    log.info(this.header.marker(), "Komprimerer melding")
    return this.copy(
        processedPayload = gZipUtil.compress(this.processedPayload),
        komprimert = true
    )
}

fun Melding.verifiserSignatur(): Melding {
    log.info(this.header.marker(), "Verifiserer signatur")
    signatureVerifisering.validate(this.processedPayload)
    return this.copy(
        signaturVerifisert = true
    )
}
fun Melding.krypter(): Melding {
    log.info(this.header.marker(), "Krypterer melding")
    val gyldigSertifikat = header.to.partyId.map {
        createX509Certificate(
            hentKrypteringssertifikat(header.cpaId, it)
        ) }
        .filter{ it.erGyldig() }
        .first() // TODO skal mer til for å bestemme hvilket sertifikat?

    return this.copy(
        processedPayload = krypterDokument(this.processedPayload, gyldigSertifikat),
        kryptert = true
    )
}

