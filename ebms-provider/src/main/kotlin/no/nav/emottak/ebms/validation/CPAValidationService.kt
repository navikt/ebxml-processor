package no.nav.emottak.ebms.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.validateSignature
import no.nav.emottak.message.exception.EbmsException
import no.nav.emottak.message.model.Direction
import no.nav.emottak.message.model.Direction.IN
import no.nav.emottak.message.model.Direction.OUT
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.ErrorCode
import no.nav.emottak.message.model.Feil
import no.nav.emottak.message.model.MessagingCharacteristicsRequest
import no.nav.emottak.message.model.MessagingCharacteristicsResponse
import no.nav.emottak.message.model.ValidationRequest
import no.nav.emottak.message.model.ValidationResult
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.validation.CPAValidationService")

open class CPAValidationService(val httpClient: CpaRepoClient) {

    suspend fun validateIncomingMessage(message: EbmsMessage, checkSignature: Boolean = true): ValidationResult =
        getValidationResult(IN, message).also {
            validateResult(
                validationResult = it,
                message = message,
                checkSignature = checkSignature
            )
        }

    suspend fun validateOutgoingMessage(message: EbmsMessage): ValidationResult =
        getValidationResult(OUT, message).also {
            validateResult(
                validationResult = it,
                message = message,
                checkSignature = false
            )
        }

    suspend fun getMessageCharacteristicsType(message: EbmsMessage): MessagingCharacteristicsResponse = withContext(Dispatchers.IO) {
        httpClient.getMessagingCharacteristics(
            request = MessagingCharacteristicsRequest(
                requestId = message.requestId,
                cpaId = message.cpaId,
                partyIds = message.addressing.from.partyId,
                role = message.addressing.from.role,
                service = message.addressing.service,
                action = message.addressing.action
            )
        )
    }.also {
        log.debug(
            "Message characteristics strategy for message {}: duplicateElimination={}, ackRequested={}, ackSignatureRequested={}",
            message.requestId,
            it.duplicateElimination,
            it.ackRequested,
            it.ackSignatureRequested
        )
    }

    private suspend fun getValidationResult(direction: Direction, message: EbmsMessage): ValidationResult {
        val validationRequest = ValidationRequest(
            direction,
            message.messageId,
            message.conversationId,
            message.cpaId,
            message.addressing,
            message.refToMessageId
        )
        val validationResult = withContext(Dispatchers.IO) {
            httpClient.postValidate(message.requestId, validationRequest)
        }
        return validationResult
    }

    open fun validateResult(validationResult: ValidationResult, message: EbmsMessage, checkSignature: Boolean): ValidationResult {
        if (!validationResult.valid()) throw EbmsException(validationResult.error!!)
        if (checkSignature) {
            runCatching {
                message.validateSignature(validationResult.payloadProcessing!!.signingCertificate)
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
