package no.nav.emottak.payload

import jakarta.xml.bind.JAXBElement
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.melding.model.Direction
import no.nav.emottak.melding.model.Payload
import no.nav.emottak.melding.model.PayloadRequest
import no.nav.emottak.melding.model.PayloadResponse
import no.nav.emottak.payload.crypto.Dekryptering
import no.nav.emottak.payload.crypto.Kryptering
import no.nav.emottak.payload.crypto.PayloadSignering
import no.nav.emottak.payload.crypto.dekryperingConfig
import no.nav.emottak.payload.crypto.payloadSigneringConfig
import no.nav.emottak.payload.util.GZipUtil
import no.nav.emottak.payload.util.XmlMarshaller
import no.nav.emottak.payload.util.unmarshal
import no.nav.emottak.util.createDocument
import no.nav.emottak.util.getByteArrayFromDocument
import no.nav.emottak.util.signatur.SignaturVerifisering
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.transform.dom.DOMResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory

class Processor(
    private val kryptering: Kryptering = Kryptering(),
    private val dekryptering: Dekryptering = Dekryptering(dekryperingConfig),
    private val signering: PayloadSignering = PayloadSignering(payloadSigneringConfig),
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
            if (processConfig.kryptering) dekryptering.dekrypter(it.bytes, false) else it.bytes
        }.let {
            if (processConfig.komprimering) gZipUtil.uncompress(it) else it
        }.let {
            if (processConfig.signering) signatureVerifisering.validate(it)
            it
        }.let {
            payloadRequest.payload.copy(bytes = it)
        }
    }

    private fun shouldThrowExceptionForTestPurposes(bytes: ByteArray) {
        val fnr = try {
            log.info("Evaluering av kandidat på negative apprec test")
            val payloadMsgHead = unmarshal(bytes, MsgHead::class.java)
            val egenandelforesporsel = payloadMsgHead.document.first().refDoc.content.any.first() as JAXBElement<*>
            val res = DOMResult()
            XmlMarshaller().marshal(egenandelforesporsel, res)
            val document: Document = res.node as Document
            val xPath: XPath = XPathFactory.newInstance().newXPath()
            val borgerFnrExpression = xPath.compile("/EgenandelForesporsel/HarBorgerFrikort/BorgerFnr")
            borgerFnrExpression.evaluate(document)
        } catch (e: Exception) {
            log.error("Payload processor: Klarer ikke å parse dokumenten via xpath", e)
            ""
        }
        if (fnr == "58116541813") {
            log.info("Negative apprect test case aktivert.")
            throw RuntimeException("Fikk rart fnr, kaster exception")
        }
    }

    private fun processOutgoing(payloadRequest: PayloadRequest): Payload {
        val processConfig = payloadRequest.processing.processConfig ?: throw RuntimeException("Processing configuration not defined for message with Id ${payloadRequest.messageId}")
        return payloadRequest.payload.let {
            if (processConfig.signering) getByteArrayFromDocument(signering.signerXML(createDocument(ByteArrayInputStream(it.bytes)))) else it.bytes
        }.let {
            if (processConfig.komprimering) gZipUtil.compress(it) else it
        }.let {
            if (processConfig.kryptering) {
                kryptering.krypter(it, payloadRequest.processing.encryptionCertificate).let {
                    payloadRequest.payload.copy(bytes = it, contentType = "application/pkcs7-mime")
                }
            } else {
                payloadRequest.payload.copy(bytes = it)
            }
        }
    }
}
