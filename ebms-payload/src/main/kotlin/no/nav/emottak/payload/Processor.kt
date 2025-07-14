package no.nav.emottak.payload

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.message.model.ProcessConfig
import no.nav.emottak.payload.crypto.Dekryptering
import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.getEncryptionDetails
import no.nav.emottak.payload.helseid.NinResolver
import no.nav.emottak.payload.juridisklogg.JuridiskLoggService
import no.nav.emottak.payload.util.EventRegistrationService
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignaturVerifisering
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
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
    private val ninResolver: NinResolver = NinResolver()

    suspend fun loggMessageToJuridiskLogg(payloadRequest: PayloadRequest): String? {
        log.info(payloadRequest.marker(), "Save message to juridisk logg")
        try {
            return juridiskLogging.logge(payloadRequest).also {
                eventRegistrationService.registerEvent(
                    EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
                    payloadRequest,
                    Json.encodeToString(
                        mapOf(EventDataType.JURIDISK_LOGG_ID.value to it)
                    )
                )
            }
        } catch (e: Exception) {
            log.error(payloadRequest.marker(), "Exception occurred while saving message to juridisk logg", e)
            throw e
        }
    }

    suspend fun convertToReadablePayload(payloadRequest: PayloadRequest, encrypted: Boolean, compressed: Boolean): Payload {
        return payloadRequest.payload.copy(
            bytes = payloadRequest.payload.bytes.let {
                when (encrypted) {
                    true -> decrypt(it, payloadRequest)
                    false -> it
                }
            }.let {
                when (compressed) {
                    true -> decompress(it, payloadRequest)
                    false -> it
                }
            }
        )
    }

    suspend fun validateReadablePayload(
        marker: Marker,
        payload: Payload,
        payloadRequest: PayloadRequest,
        processConfig: ProcessConfig
    ): Payload {
        if (processConfig.signering) {
            log.debug(marker, "Validating signature for payload")

            signatureVerifisering.validate(payload.bytes).also {
                eventRegistrationService.registerEvent(
                    EventType.SIGNATURE_CHECK_SUCCESSFUL,
                    payloadRequest
                )
            }
        }
        return if (processConfig.ocspSjekk) {
            log.debug(marker, "Validating for payload in validateOcsp flow")
            val domDocument = createDocument(ByteArrayInputStream(payload.bytes))

            val xmlSignature = domDocument.retrieveSignatureElement()

            val certificateFromSignature = xmlSignature.keyInfo.x509Certificate

            var signedByFnr: String? = ninResolver.resolve(domDocument, certificateFromSignature)

            log.debug(marker, "Validating OCSP for payload: Step 5 copy")
            payload.copy(signedBy = signedByFnr).also {
                eventRegistrationService.registerEvent(
                    EventType.OCSP_CHECK_SUCCESSFUL,
                    payloadRequest
                )
            }
        } else {
            payload
        }
    }

    suspend fun processOutgoing(payloadRequest: PayloadRequest): Payload {
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
                true -> compress(it, payloadRequest)
                false -> it
            }
        }.let {
            when (processConfig.kryptering) {
                true -> encrypt(it, payloadRequest)
                false -> payloadRequest.payload.copy(bytes = it)
            }
        }
    }

    private suspend fun encrypt(payload: ByteArray, payloadRequest: PayloadRequest): Payload {
        val certificate = createX509Certificate(payloadRequest.processing.encryptionCertificate)
        return with(certificate) {
            kryptering.krypter(payload, this).let { kryptertPayload ->
                log.info(payloadRequest.marker(), "Payload kryptert for ${this.subjectX500Principal.name}")
                payloadRequest.payload.copy(bytes = kryptertPayload, contentType = "application/pkcs7-mime")
            }
        }.also {
            eventRegistrationService.registerEvent(
                EventType.MESSAGE_ENCRYPTED,
                payloadRequest,
                Json.encodeToString(
                    mapOf(EventDataType.ENCRYPTION_DETAILS.value to getEncryptionDetails(certificate))
                )
            )
        }
    }

    private suspend fun decrypt(payload: ByteArray, payloadRequest: PayloadRequest): ByteArray {
        return dekryptering.dekrypter(payload, false).also {
            eventRegistrationService.registerEvent(
                EventType.MESSAGE_DECRYPTED,
                payloadRequest
            )
        }
    }

    private suspend fun compress(payload: ByteArray, payloadRequest: PayloadRequest): ByteArray {
        return gZipUtil.compress(payload)
            .also { log.info(payloadRequest.marker(), "Payload komprimert") }
            .also {
                eventRegistrationService.registerEvent(
                    EventType.MESSAGE_COMPRESSED,
                    payloadRequest
                )
            }
    }

    private suspend fun decompress(payload: ByteArray, payloadRequest: PayloadRequest): ByteArray {
        return gZipUtil.uncompress(payload).also {
            eventRegistrationService.registerEvent(
                EventType.MESSAGE_DECOMPRESSED,
                payloadRequest
            )
        }
    }
}
