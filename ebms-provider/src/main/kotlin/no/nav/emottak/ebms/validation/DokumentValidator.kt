package no.nav.emottak.ebms.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.sjekkSignature
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.SignatureDetails

class DokumentValidator(val httpClient: CpaRepoClient) {


    fun validate(dokument: EbMSDocument,signatureDetails: SignatureDetails?) {

        val messageHeader = dokument.messageHeader()

        //TODO valider sertifikat
        val header = Header(messageHeader.messageData.messageId,
                            messageHeader.conversationId,
                            messageHeader.cpaId,
                            //TODO select specific partyID?
                            Party(messageHeader.to.partyId.first().type!!, messageHeader.to.partyId.first().value!!,messageHeader.to.role!!),
                            Party(messageHeader.from.partyId.first().type!!, messageHeader.from.partyId.first().value!!,messageHeader.from.role!!),
                            messageHeader.service.value!!,
                            messageHeader.action)
        runBlocking {
            httpClient.postValidate(header)
        }
        if (signatureDetails == null) throw Exception("Unable to retrieve signature details")
        dokument.sjekkSignature(signatureDetails)

    }
}