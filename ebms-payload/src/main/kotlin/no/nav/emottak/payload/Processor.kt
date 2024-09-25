package no.nav.emottak.payload

import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.crypto.KeyStore
import no.nav.emottak.melding.model.Direction
import no.nav.emottak.melding.model.Payload
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.payload.crypto.Dekryptering
import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.ocspstatus.OcspStatusService
import no.nav.emottak.payload.ocspstatus.trustStoreConfig
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.payload.util.unmarshal
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.marker
import no.nav.emottak.util.retrieveSignatureElement
import no.nav.emottak.util.signatur.SignaturVerifisering
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory

val processor = Processor()
class Processor(
    private val kryptering: Kryptering = Kryptering(),
    private val dekryptering: Dekryptering = Dekryptering(),
    private val signering: PayloadSignering = PayloadSignering(),
    private val gZipUtil: GZipUtil = GZipUtil(),
    private val signatureVerifisering: SignaturVerifisering = SignaturVerifisering()
) {

    fun process(payloadRequest: PayloadRequest): PayloadResponse {
        val processedPayload = when (payloadRequest.direction) {
            Direction.IN -> processIncoming(payloadRequest)
            Direction.OUT -> processOutgoing(payloadRequest)
            else -> throw RuntimeException("Direction can be either IN or Out")
        }
        return PayloadResponse(
            processedPayload
        )
    }

    private fun processIncoming(payloadRequest: PayloadRequest): Payload {
        val processConfig = payloadRequest.processing.processConfig ?: throw RuntimeException("Processing configuration not defined for message with Id ${payloadRequest.messageId}")

        shouldThrowExceptionForTestPurposes(payloadRequest.payload.bytes)

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
                val signedOf = OcspStatusService(defaultHttpClient().invoke(), KeyStore(payloadSigneringConfig()), KeyStore(trustStoreConfig())).getOCSPStatus(certificateFromSignature).fnr
                it.copy(signedOf = signedOf)
            } else {
                it
            }
        }
    }

    private fun processOutgoing(payloadRequest: PayloadRequest): Payload {
        val processConfig = payloadRequest.processing.processConfig ?: throw RuntimeException("Processing configuration not defined for message with Id ${payloadRequest.messageId}")
        return payloadRequest.payload.let {
            when (processConfig.signering) {
                true -> {
                    getByteArrayFromDocument(signering.signerXML(createDocument(ByteArrayInputStream(it.bytes))))
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

    private fun shouldThrowExceptionForTestPurposes(bytes: ByteArray) {
        val fnr = try {
            log.info("Evaluering av kandidat på negative apprec test")
            val payloadMsgHead = unmarshal(bytes, MsgHead::class.java)
            val egenandelforesporsel = payloadMsgHead.document.first().refDoc.content.any.first() as Element
            val xPath: XPath = XPathFactory.newInstance().newXPath()
            val borgerFnrExpressionV1 =
                xPath.compile("""/*[local-name() = 'EgenandelForesporsel']/*[local-name() = 'HarBorgerFrikort']/*[local-name() = 'BorgerFnr']/text()""")
            val borgerFnrExpressionV2 =
                xPath.compile("""/*[local-name() = 'EgenandelForesporselV2']/*[local-name() = 'HarBorgerFrikort']/*[local-name() = 'BorgerFnr']/text()""")

            log.info("Evaluating for version1: ${borgerFnrExpressionV1.evaluate(egenandelforesporsel.ownerDocument)}")
            log.info("Evaluating for version2: ${borgerFnrExpressionV2.evaluate(egenandelforesporsel.ownerDocument)}")

            borgerFnrExpressionV1.evaluate(egenandelforesporsel.ownerDocument).takeIf { !it.isNullOrBlank() }
                ?: borgerFnrExpressionV2.evaluate(egenandelforesporsel.ownerDocument)
        } catch (e: Exception) {
            log.error("Payload processor: Klarer ikke å parse dokumenten via xpath", e)
            ""
        }
        if (fnr == "58116541813") {
            log.info("Negative apprect test case aktivert.")
            throw RuntimeException("Fikk rart fnr, kaster exception")
        }
    }
}
