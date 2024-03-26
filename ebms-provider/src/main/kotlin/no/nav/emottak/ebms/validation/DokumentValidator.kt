package no.nav.emottak.ebms.validation

import jakarta.xml.bind.JAXBElement
import javax.xml.transform.dom.DOMResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory
import kotlinx.coroutines.runBlocking
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbmsMessage
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.ebms.xml.XmlMarshaller
import no.nav.emottak.ebms.xml.unmarshal
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.decodeBase64
import org.slf4j.LoggerFactory
import org.w3c.dom.Document

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")
class DokumentValidator(val httpClient: CpaRepoClient) {

    fun validateIn(message: EbmsMessage) = validate(message, true)
    fun validateOut(message: EbmsMessage) = validate(message, false)


    private fun shouldThrowExceptionForTestPurposes(bytes: ByteArray) {
        val fnr = try {
            val payloadMsgHead = unmarshal(String(bytes), MsgHead::class.java)
            val egenandelforesporsel = payloadMsgHead.document.first().refDoc.content.any.first() as JAXBElement<*>
            val res = DOMResult()
            XmlMarshaller().marshal(egenandelforesporsel, res)
            val document: Document = res.node as Document
            val xPath: XPath = XPathFactory.newInstance().newXPath()
            val borgerFnrExpression = xPath.compile("/EgenandelForesporselV2/HarBorgerEgenandelfritak/BorgerFnr")
            borgerFnrExpression.evaluate(document)
        } catch (e: Exception) {
            ""
        }
        if (fnr == "20118690681") throw RuntimeException("Dette er et test fnr 20118690681, kaster exception")
    }

    private fun validate(message: EbmsMessage, sjekSignature: Boolean): ValidationResult {
        val validationRequest = ValidationRequest(message.messageId, message.conversationId, message.cpaId, message.addressing)
        val validationResult = runBlocking {
            httpClient.postValidate(message.requestId, validationRequest)
        }

        if (!validationResult.valid()) throw EbmsException(validationResult.error!!)
        if (sjekSignature) {
            runCatching {
                shouldThrowExceptionForTestPurposes((message as PayloadMessage).payload.bytes)
                message.sjekkSignature(validationResult.payloadProcessing!!.signingCertificate)
            }.onFailure {
                throw EbmsException(
                    (validationResult.error ?: listOf()) + listOf(
                        Feil(
                            ErrorCode.SECURITY_FAILURE,
                            "Signeringsfeil: ${it.message}"
                        )
                    ),
                    it
                )
            }
        }
        return validationResult
    }
}
