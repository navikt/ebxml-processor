package no.nav.emottak.payload

import no.nav.emottak.melding.model.Direction
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.crypto.Dekryptering
import no.nav.emottak.util.crypto.Kryptering
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.signatur.SignaturVerifisering
import no.nav.emottak.util.signatur.Signering
import java.io.ByteArrayInputStream

class Processor(
    private val kryptering: Kryptering = Kryptering(),
    private val dekryptering: Dekryptering = Dekryptering(),
    private val signering: Signering = Signering(),
    private val gZipUtil: GZipUtil = GZipUtil(),
    private val signatureVerifisering: SignaturVerifisering = SignaturVerifisering()
) {

    fun process(payloadRequest: PayloadRequest): PayloadResponse {
        return try {
            val processedPayload = when (payloadRequest.direction) {
                Direction.IN -> processIncoming(payloadRequest)
                Direction.OUT -> processOutgoing(payloadRequest)
                else -> throw RuntimeException("Direction can be either IN or Out")
            }
            PayloadResponse(
                payloadId = payloadRequest.payloadId,
                processedPayload = processedPayload
            )
        } catch (e: Exception) {
            PayloadResponse(
                payloadId = payloadRequest.payloadId,
                processedPayload = payloadRequest.payload,
                error = Feil(ErrorCode.UNKNOWN, e.localizedMessage, "Error")
            )
        }
    }

    private fun processIncoming(payloadRequest: PayloadRequest): ByteArray {
        val processConfig = payloadRequest.processing.processConfig ?: return payloadRequest.payload
        return payloadRequest.payload.let {
            if (processConfig.kryptering) dekryptering.dekrypter(it, false) else it
        }.let {
            if (processConfig.komprimering) gZipUtil.uncompress(it) else it
        }.let {
            if (processConfig.signering) signatureVerifisering.validate(it)
            it
        }
    }

    private fun processOutgoing(payloadRequest: PayloadRequest): ByteArray {
        val processConfig = payloadRequest.processing.processConfig ?: return payloadRequest.payload
        return payloadRequest.payload.let {
            if (processConfig.signering) getByteArrayFromDocument(signering.signerXML(createDocument(ByteArrayInputStream(it)))) else it
        }.let {
            if (processConfig.komprimering) gZipUtil.compress(it) else it
        }.let {
            if (processConfig.kryptering) {
                kryptering.krypter(it, payloadRequest.processing.encryptionCertificate)
            } else {
                it
            }
        }
    }
}
