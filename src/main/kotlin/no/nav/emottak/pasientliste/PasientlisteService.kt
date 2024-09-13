package no.nav.emottak.pasientliste

import no.nav.emottak.fellesformat.FellesFormatXmlMarshaller
import no.nav.emottak.fellesformat.wrapMessageInEIFellesFormat
import no.nav.emottak.melding.model.SendInRequest
import no.nav.emottak.melding.model.SendInResponse

object PasientlisteService {
    fun pasientlisteForesporsel(request: SendInRequest): SendInResponse {
        return when (request.addressing.action) {
            "HentPasientliste" -> hentPasientListe(request)
            else -> throw NotImplementedError("Action: ${request.addressing.action} for service: ${request.addressing.service} is not implemented")
        }
    }

    private fun hentPasientListe(request: SendInRequest): SendInResponse {
        val fellesformatRequest = wrapMessageInEIFellesFormat(request)
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
