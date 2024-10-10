package no.nav.emottak.ebms.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbmsMessage
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")

class DokumentValidator(val httpClient: CpaRepoClient) {

    suspend fun validateIn(message: EbmsMessage) = validate(message, true)
    suspend fun validateOut(message: EbmsMessage) = validate(message, false)

    private suspend fun validate(message: EbmsMessage, sjekSignature: Boolean): ValidationResult {
        val validationRequest =
            ValidationRequest(message.messageId, message.conversationId, message.cpaId, message.addressing)
        val validationResult = withContext(Dispatchers.IO) {
            httpClient.postValidate(message.requestId, validationRequest)
        }

        if (!validationResult.valid()) throw EbmsException(validationResult.error!!)
        if (sjekSignature) {
            runCatching {
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
