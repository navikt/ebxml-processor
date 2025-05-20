package no.nav.emottak.payload

import java.io.ByteArrayInputStream
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import no.nav.emottak.crypto.KeyStoreManager
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadRequest
import no.nav.emottak.payload.crypto.Dekryptering
import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.helseid.helseIdValidator
import no.nav.emottak.payload.helseid.util.util.xades.getAllEncapsulatedCertificatesByPrincipal
import no.nav.emottak.payload.juridisklogg.JuridiskLoggService
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.createX509Certificate
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignaturVerifisering
import org.slf4j.Marker
import org.w3c.dom.Document


val processor = Processor()

class Processor(
    private val kryptering: Kryptering = Kryptering(),
    private val dekryptering: Dekryptering = Dekryptering(),
    private val signering: PayloadSignering = PayloadSignering(),
    private val gZipUtil: GZipUtil = GZipUtil(),
    private val signatureVerifisering: SignaturVerifisering = SignaturVerifisering(),
    private val juridiskLogging: JuridiskLoggService = JuridiskLoggService(),
) {

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

    suspend fun validateReadablePayload(
        marker: Marker,
        payload: Payload,
        validateSignature: Boolean,
        validateOcsp: Boolean
    ): Payload {
        if (validateSignature) {
            log.debug(marker, "Validating signature for payload")

            signatureVerifisering.validate(payload.bytes)
        }
        return if (validateOcsp) {
            log.debug(marker, "Validating for payload in validateOcsp flow")
            val domDocument = createDocument(ByteArrayInputStream(payload.bytes))

            val xmlSignature = domDocument.retrieveSignatureElement()

            val certificateFromSignature = xmlSignature.keyInfo.x509Certificate

            var signedByFnr: String? = getNin(domDocument, certificateFromSignature, marker)

            payload.copy(signedBy = signedByFnr)
        } else {
            payload
        }
    }

    private suspend fun getNin(
        domDocument: Document,
        certificateFromSignature: X509Certificate,
        marker: Marker
    ): String? {
        val helseIdToken = helseIdValidator.getHelseIDTokenNodesFromDocument(doc = domDocument)
        var signedByFnr: String? = if (!helseIdToken.isNullOrBlank()) {
            log.debug(marker, "Validating HelseID Token for payload")
            try {
                val timeStamp = ZonedDateTime.parse(
                    domDocument.getElementsByTagNameNS("http://www.kith.no/xmlstds/msghead/2006-05-24", "GenDate")
                        .item(0)?.textContent
                )
                val certificatesByPrincipal = getAllEncapsulatedCertificatesByPrincipal(domDocument)
                helseIdValidator.getValidatedNin(
                    helseIdToken,
                    timeStamp = timeStamp,
                    certificates = certificatesByPrincipal.values
                ).also { log.debug(marker, "Found NIN '$it' from HelseID") }
            } catch (e: Exception) {
                log.error("Failed during helseID check", e)
                null
            }
        } else {
            null
        }

        if (signedByFnr == null) {
            log.debug(marker, "Fallback to validating OCSP for payload: getting fnr")
            signedByFnr = ocspStatusService.getOCSPStatus(certificateFromSignature).fnr
        }

        return signedByFnr
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
