package no.nav.emottak.ebms.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.EbmsMessage
import no.nav.emottak.ebms.PayloadMessage
import no.nav.emottak.ebms.ebxml.toValidationRequest
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.sjekkSignature
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")
class DokumentValidator(val httpClient: CpaRepoClient) {

    fun validateOut(contentId: String, validationRequest: ValidationRequest): ValidationResult {
        return runBlocking {
            httpClient.postValidate(contentId, validationRequest)
        }
    }

    fun validateOut(contentId: String,payloadMessage: PayloadMessage) : ValidationResult {
        return runBlocking {
            httpClient.postValidate(contentId,ValidationRequest(contentId,payloadMessage.conversationId,payloadMessage.cpaId,payloadMessage.addressing))
        }
    }

    fun validateIn2(message: EbmsMessage): ValidationResult {
        val validationRequest = ValidationRequest(message.messageId,message.conversationId,message.cpaId,message.addressing)


        val validationResult = runBlocking {
            httpClient.postValidate(message.requestId, validationRequest)
        }

        if (!validationResult.valid()) return validationResult
        runCatching {
            message.sjekkSignature(validationResult.payloadProcessing!!.signingCertificate)
        }.onFailure {
            log.error("Signaturvalidering feilet ${it.message}", it)
            return validationResult.copy(
                error = (validationResult.error ?: listOf()) + listOf(
                    Feil(
                        ErrorCode.SECURITY_FAILURE,
                        "Feil signature"
                    )
                )
            )
        }

        return validationResult
    }


    fun validateIn(dokument: EbMSDocument): ValidationResult {
        val validationRequest = dokument.messageHeader().toValidationRequest()

        val validationResult = runBlocking {
            httpClient.postValidate(dokument.requestId, validationRequest)
        }

        if (!validationResult.valid()) return validationResult
        runCatching {
            dokument.sjekkSignature(validationResult.payloadProcessing!!.signingCertificate)
        }.onFailure {
            log.error(dokument.messageHeader().marker(), "Signaturvalidering feilet ${it.message}", it)
            return validationResult.copy(
                error = (validationResult.error ?: listOf()) + listOf(
                    Feil(
                        ErrorCode.SECURITY_FAILURE,
                        "Feil signature"
                    )
                )
            )
        }

        return validationResult
    }
}
