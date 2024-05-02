package no.nav.emottak.ebms.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.kith.xmlstds.msghead._2006_05_24.MsgHead
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbmsMessage
import no.nav.emottak.ebms.model.PayloadMessage
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.xml.unmarshal
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")

class DokumentValidator(val httpClient: CpaRepoClient) {

    suspend fun validateIn(message: EbmsMessage) = validate(message, true)
    suspend fun validateOut(message: EbmsMessage) = validate(message, false)

    private fun shouldThrowExceptionForTestPurposes(bytes: ByteArray) {
        val fnr = try {
            log.info("Evaluering av kandidat på feil signal test")
            val payloadMsgHead = unmarshal(String(bytes), MsgHead::class.java)
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
            log.error("Klarer ikke å parse dokumenten via xpath", e)
            ""
        }
        if (fnr == "20118690681") throw RuntimeException("Dette er et test fnr 20118690681, kaster exception")
    }

    private suspend fun validate(message: EbmsMessage, sjekSignature: Boolean): ValidationResult {
        val validationRequest =
            ValidationRequest(message.messageId, message.conversationId, message.cpaId, message.addressing)
        val validationResult = withContext(Dispatchers.IO) {
            httpClient.postValidate(message.requestId, validationRequest)
        }

        if (!validationResult.valid()) throw EbmsException(validationResult.error!!)
        if (sjekSignature) {
            runCatching {
                if (message is PayloadMessage) {
                    shouldThrowExceptionForTestPurposes(message.payload.bytes)
                }
                message.sjekkSignature(validationResult.payloadProcessing!!.signingCertificate)
            }.onFailure {
                log.warn(message.marker(), "Signatursjekk har feilet", it)
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
