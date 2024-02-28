package no.nav.emottak.ebms.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbmsMessage
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.ValidationRequest
import no.nav.emottak.melding.model.ValidationResult
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")
class DokumentValidator(val httpClient: CpaRepoClient) {

    fun validateIn(message: EbmsMessage) = validate(message, true)
    fun validateOut(message: EbmsMessage) = validate(message, false)

    private fun validate(message: EbmsMessage, sjekSignature: Boolean): ValidationResult {
        val validationRequest = ValidationRequest(message.messageId, message.conversationId, message.cpaId, message.addressing)
        val validationResult = runBlocking {
            httpClient.postValidate(message.requestId, validationRequest)
        }

        if (!validationResult.valid()) throw EbmsException(validationResult.error!!)
        if (sjekSignature) {
            runCatching {
                message.sjekkSignature(validationResult.payloadProcessing!!.signingCertificate)
            }.onFailure {
                log.error("Signaturvalidering feilet ${it.message}", it)
                throw EbmsException(
                    (validationResult.error ?: listOf()) + listOf(
                        Feil(
                            ErrorCode.SECURITY_FAILURE,
                            "Feil signature"
                        )
                    ),
                    it
                )
            }
        }
        return validationResult
    }
}
