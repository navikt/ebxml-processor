package no.nav.emottak.pasientliste

import no.nav.emottak.fellesformat.FellesFormatXmlMarshaller
import no.nav.emottak.fellesformat.wrapMessageInEIFellesFormat
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse

object PasientlisteService {

    const val CONFLICT_SIGNING_SSN = "Sender FNR og legen som har signert meldingen skall ikke vare forskjelige."
    fun pasientlisteForesporsel(request: SendInRequest): SendInResponse {
        return when (request.addressing.action) {
            "HentPasientliste" -> hentPasientListe(request)
            else -> throw NotImplementedError("Action: ${request.addressing.action} for service: ${request.addressing.service} is not implemented")
        }
    }

    private fun hentPasientListe(request: SendInRequest): SendInResponse {
        val fellesformatRequest = wrapMessageInEIFellesFormat(request)
        val senderFnr = fellesformatRequest.msgHead.msgInfo.sender.organisation.healthcareProfessional.ident.first().id
        if (senderFnr != request.signedOf) {
            throw RuntimeException(CONFLICT_SIGNING_SSN)
        }
        val fellesformatResponse = PasientlisteClient.hentPasientliste(fellesformatRequest)

        return SendInResponse(
            request.messageId,
            request.conversationId,
            request.addressing.replyTo(
                fellesformatResponse.mottakenhetBlokk.ebService,
                fellesformatResponse.mottakenhetBlokk.ebAction
            ),
            FellesFormatXmlMarshaller.marshalToByteArray(fellesformatResponse.appRec)
        )
    }
}
