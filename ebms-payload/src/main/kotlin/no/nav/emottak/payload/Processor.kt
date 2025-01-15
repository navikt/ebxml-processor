package no.nav.emottak.payload

import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
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

    suspend fun loggMessageToJuridiskLogg(payloadRequest: PayloadRequest): String? {
        log.info(payloadRequest.marker(), "Save message to juridisk logg")
        try {
            return juridiskLogging.logge(payloadRequest)
        } catch (e: Exception) {
            log.error(payloadRequest.marker(), "Exception occurred while saving message to juridisk logg", e)
            throw e
        }
    }

    fun convertToReadablePayload(payload: Payload, encrypted: Boolean, compressed: Boolean): Payload {
        return payload.copy(
            bytes = payload.bytes.let {
                when (encrypted) {
                    true -> dekryptering.dekrypter(it, false)
                    false -> it
                }
            }.let {
                when (compressed) {
                    true -> gZipUtil.uncompress(it)
                    false -> it
                }
            }
        )
    }

    suspend fun validateReadablePayload(payload: Payload, validateSignature: Boolean, validateOcsp: Boolean): Payload {
        if (validateSignature) {
            signatureVerifisering.validate(payload.bytes)
        }
        return if (validateOcsp) {
            val dom = createDocument(ByteArrayInputStream(payload.bytes))
            val xmlSignature = dom.retrieveSignatureElement()
            val certificateFromSignature = xmlSignature.keyInfo.x509Certificate
            val signedBy = OcspStatusService(defaultHttpClient().invoke(), KeyStore(payloadSigneringConfig()), KeyStore(trustStoreConfig())).getOCSPStatus(certificateFromSignature).fnr
            payload.copy(signedBy = signedBy)
        } else {
            payload
        }
    }

    fun processOutgoing(payloadRequest: PayloadRequest): Payload {
        val processConfig = payloadRequest.processing.processConfig
        return payloadRequest.payload.let {
            when (processConfig.signering) {
                true -> {
                    getByteArrayFromDocument(
                        signering.signerXML(
                            createDocument(ByteArrayInputStream(it.bytes)),
                            payloadRequest.processing.signingCertificate
                        )
                    )
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
}
