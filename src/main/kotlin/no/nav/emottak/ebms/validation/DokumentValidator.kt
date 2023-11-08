package no.nav.emottak.ebms.validation

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.CpaRepoClient
import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.model.sjekkSignature
import no.nav.emottak.melding.model.Header
import no.nav.emottak.melding.model.Party
import no.nav.emottak.melding.model.PartyId
import no.nav.emottak.melding.model.Processing
import no.nav.emottak.melding.model.SignatureDetails
import kotlin.jvm.Throws

class DokumentValidator(val httpClient: CpaRepoClient) {


    @Throws(Exception::class)
    fun validate(dokument: EbMSDocument) : Processing {

        val messageHeader = dokument.messageHeader()

        //TODO valider sertifikat
        val header = Header(messageHeader.messageData.messageId,
                            messageHeader.conversationId,
                            messageHeader.cpaId,
                            //TODO select specific partyID?
                            Party(messageHeader.to.partyId.map { PartyId(it.type!!, it.value!!) }, messageHeader.to.role!!),
                            //Party(messageHeader.to.partyId.first().type!!, messageHeader.to.partyId.first().value!!,messageHeader.to.role!!),
                            Party(messageHeader.from.partyId.map { PartyId(it.type!!, it.value!!) }, messageHeader.from.role!!),
                            messageHeader.service.value!!,
                            messageHeader.action)
        val validationResponse = runBlocking {
            httpClient.postValidate(header)
        }

        if (!validationResponse.valid()) throw Exception("Validation failed")
        dokument.sjekkSignature(validationResponse.processing!!.signingCertificate)
        return validationResponse.processing!!
    }
}