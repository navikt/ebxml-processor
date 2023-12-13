package no.nav.emottak.ebms.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.sjekkSignature
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.ValidationResult
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.DokumentValidator")
class DokumentValidator(val httpClient: CpaRepoClient) {

    fun validate(dokument: EbMSDocument): ValidationResult {
        val messageHeader = dokument.messageHeader()

        // TODO valider sertifikat
        val header = Header(
            messageHeader.messageData.messageId,
            messageHeader.conversationId,
            messageHeader.cpaId,
            // TODO select specific partyID?
            Party(messageHeader.to.partyId.map { PartyId(it.type!!, it.value!!) }, messageHeader.to.role!!),
            // Party(messageHeader.to.partyId.first().type!!, messageHeader.to.partyId.first().value!!,messageHeader.to.role!!),
            Party(messageHeader.from.partyId.map { PartyId(it.type!!, it.value!!) }, messageHeader.from.role!!),
            messageHeader.service.value!!,
            messageHeader.action
        )
        val validationResult = runBlocking {
            httpClient.postValidate(dokument.contentId, header)
        }

        if (!validationResult.valid()) return validationResult
        runCatching {
            dokument.sjekkSignature(validationResult.processing!!.signingCertificate)
        }.onFailure {
            log.error(dokument.messageHeader().marker(), "Signaturvalidering feilet ${it.message}", it)
            return ValidationResult(
                validationResult.processing,
                (validationResult.error ?: listOf()) + listOf(
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
