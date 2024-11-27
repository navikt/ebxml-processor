package no.nav.emottak.payload

import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.PayloadResponse
import no.nav.emottak.payload.crypto.Dekryptering
import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.juridisklogg.JuridiskLoggService
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.trustStoreConfig
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignaturVerifisering
import java.io.ByteArrayInputStream

val processor = Processor()
class Processor(
    private val kryptering: Kryptering = Kryptering(),
    private val dekryptering: Dekryptering = Dekryptering(),
    private val signering: PayloadSignering = PayloadSignering(),
    private val gZipUtil: GZipUtil = GZipUtil(),
    private val signatureVerifisering: SignaturVerifisering = SignaturVerifisering(),
    private val juridiskLogging: JuridiskLoggService = JuridiskLoggService()
) {

    suspend fun process(payloadRequest: PayloadRequest): PayloadResponse {
        val processedPayload = when (payloadRequest.direction) {
            Direction.IN -> processIncoming(payloadRequest)
            Direction.OUT -> processOutgoing(payloadRequest)
            else -> throw RuntimeException("Direction can be either IN or Out")
        }
        return PayloadResponse(
            processedPayload
        )
    }

    fun decrypt(bytes: ByteArray) = dekryptering.dekrypter(bytes, isBase64 = false)

    private suspend fun processIncoming(payloadRequest: PayloadRequest): Payload {
        val processConfig = payloadRequest.processing.processConfig

        loggMessageToJuridiskLogg(payloadRequest)

        return payloadRequest.payload.let {
            when (processConfig.kryptering) {
                true -> dekryptering.dekrypter(it.bytes, false).also { log.info(payloadRequest.marker(), "Payload dekryptert") }
                false -> it.bytes
            }
        }.let {
            when (processConfig.komprimering) {
                true -> gZipUtil.uncompress(it).also { log.info(payloadRequest.marker(), "Payload dekomprimert") }
                false -> it
            }
        }.let {
            if (processConfig.signering) {
                signatureVerifisering.validate(it)
                log.info(payloadRequest.marker(), "Payload signatur verifisert")
            }
            it
        }.let {
            payloadRequest.payload.copy(bytes = it)
        }.let {
            if (processConfig.ocspSjekk) {
                val dom = createDocument(ByteArrayInputStream(it.bytes))
                val signature = dom.retrieveSignatureElement()
                val certificateFromSignature = signature.keyInfo.x509Certificate
                val signedBy = OcspStatusService(defaultHttpClient().invoke(), KeyStore(payloadSigneringConfig()), KeyStore(trustStoreConfig())).getOCSPStatus(certificateFromSignature).fnr
                it.copy(signedBy = signedBy)
            } else {
                it
            }
        }
    }

    private suspend fun processOutgoing(payloadRequest: PayloadRequest): Payload {
        val processConfig = payloadRequest.processing.processConfig

        loggMessageToJuridiskLogg(payloadRequest)

        return payloadRequest.payload.let {
            when (processConfig.signering) {
                true -> {
                    getByteArrayFromDocument(signering.signerXML(createDocument(ByteArrayInputStream(it.bytes)), payloadRequest.processing.signingCertificate))
                        .also { log.info(payloadRequest.marker(), "Payload signert") }
                }
                false -> it.bytes
            }
        }.let {
            when (processConfig.komprimering) {
                true -> gZipUtil.compress(it).also { log.info(payloadRequest.marker(), "Payload komprimert") }
                false -> it
            }
        }.let {
            when (processConfig.kryptering) {
                true -> {
                    kryptering.krypter(it, payloadRequest.processing.encryptionCertificate).let {
                        log.info(payloadRequest.marker(), "Payload kryptert")
                        payloadRequest.payload.copy(bytes = it, contentType = "application/pkcs7-mime")
                    }
                }
                false -> payloadRequest.payload.copy(bytes = it)
            }
        }
    }

    private suspend fun loggMessageToJuridiskLogg(payloadRequest: PayloadRequest) {
        try {
            if (payloadRequest.processing.processConfig.juridiskLogg) {
                log.debug(payloadRequest.marker(), "Sender forespørsel til juridisk logg")
                juridiskLogging.logge(payloadRequest)
            }
        } catch (e: Exception) {
            log.error(payloadRequest.marker(), "Feil med å lage forespørsel til juridisk logg", e)
            throw e
        }
    }
}
