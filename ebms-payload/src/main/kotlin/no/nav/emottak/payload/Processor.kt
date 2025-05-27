package no.nav.emottak.payload

import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.crypto.Dekryptering
import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.juridisklogg.JuridiskLoggService
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.util.EventRegistrationService
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignaturVerifisering
import org.slf4j.Marker
import java.io.ByteArrayInputStream

class Processor(
    private val eventRegistrationService: EventRegistrationService
) {
    private val kryptering: Kryptering = Kryptering()
    private val dekryptering: Dekryptering = Dekryptering()
    private val signering: PayloadSignering = PayloadSignering()
    private val gZipUtil: GZipUtil = GZipUtil()
    private val signatureVerifisering: SignaturVerifisering = SignaturVerifisering()
    private val juridiskLogging: JuridiskLoggService = JuridiskLoggService()
    private val ocspStatusService = OcspStatusService(
        defaultHttpClient().invoke(),
        KeyStoreManager(
            payloadSigneringConfig() // TODO add commfides config
        )
    )

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

    suspend fun validateReadablePayload(marker: Marker, payload: Payload, validateSignature: Boolean, validateOcsp: Boolean): Payload {
        if (validateSignature) {
            log.debug(marker, "Validating signature for payload")

            signatureVerifisering.validate(payload.bytes)
        }
        return if (validateOcsp) {
            log.debug(marker, "Validating OCSP for payload: Step 1 create DOM")
            val dom = createDocument(ByteArrayInputStream(payload.bytes))

            log.debug(marker, "Validating OCSP for payload: Step 2 retrieve signature element")
            val xmlSignature = dom.retrieveSignatureElement()

            log.debug(marker, "Validating OCSP for payload: Step 3 get certificate from signature")
            val certificateFromSignature = xmlSignature.keyInfo.x509Certificate

            log.debug(marker, "Validating OCSP for payload: Step 4 fnr from getOCSPStatus")
            val signedBy = ocspStatusService.getOCSPStatus(certificateFromSignature).fnr

            log.debug(marker, "Validating OCSP for payload: Step 5 copy")
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
                    with(createX509Certificate(payloadRequest.processing.encryptionCertificate)) {
                        kryptering.krypter(it, this).let { kryptertPayload ->
                            log.info(payloadRequest.marker(), "Payload kryptert for ${this.subjectX500Principal.name}")
                            payloadRequest.payload.copy(bytes = kryptertPayload, contentType = "application/pkcs7-mime")
                        }
                    }
                }
                false -> payloadRequest.payload.copy(bytes = it)
            }
        }
    }
}
