package no.nav.emottak.ebms.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.HttpClientUtil
import no.nav.emottak.ebms.getPublicSigningDetails
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.sjekkSignature
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.SignatureDetails

class DokumentValidator {

    var httpClient = HttpClientUtil()


    fun validate(dokument: EbMSDocument) {

        val messageHeader = dokument.messageHeader()
        val signaturedetails: SignatureDetails = getPublicSigningDetails(messageHeader)
        val header = Header(messageHeader.messageData.messageId,
                            messageHeader.conversationId,
                            messageHeader.cpaId,
                            Party(messageHeader.to.partyId.first().value!!,messageHeader.to.role!!),
                            Party(messageHeader.from.partyId.first().value!!,messageHeader.from.role!!),
                            messageHeader.service.value!!,
                            messageHeader.action)
        runBlocking {
            httpClient.postValidate(header)
        }
        dokument.sjekkSignature(signaturedetails)

    }
}