package no.nav.emottak.ebms.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.sjekkSignature
import no.nav.emottak.ebms.util.marker
import no.nav.emottak.melding.feil.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.Direction.IN
import no.nav.emottak.message.model.Direction.OUT
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.MessageError
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")

class DokumentValidator(val httpClient: CpaRepoClient) {

    suspend fun validateIn(message: EbmsMessage): ValidationResult =
        getValidationResult(IN, message).also {
            validateResult(
                validationResult = it,
                message = message,
                checkSignature = true
            )
        }

    suspend fun validateOut(message: EbmsMessage): ValidationResult =
        getValidationResult(OUT, message).also {
            validateResult(
                validationResult = it,
                message = message,
                checkSignature = false
            )
        }

    suspend fun validateOutgoingMessageError(messageError: MessageError): ValidationResult =
        getValidationResult(OUT, messageError)

    private suspend fun getValidationResult(direction: Direction, message: EbmsMessage): ValidationResult {
        val validationRequest = ValidationRequest(
            direction,
            message.messageId,
            message.conversationId,
            message.cpaId,
            message.addressing
        )
        val validationResult = withContext(Dispatchers.IO) {
            httpClient.postValidate(message.requestId, validationRequest)
        }
        return validationResult
    }

    private fun validateResult(validationResult: ValidationResult, message: EbmsMessage, checkSignature: Boolean): ValidationResult {
        if (!validationResult.valid()) throw EbmsException(validationResult.error!!)
        if (checkSignature) {
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
