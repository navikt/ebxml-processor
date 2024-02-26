package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbmsPayloadMessage
import no.nav.emottak.melding.model.ErrorCode
import no.nav.emottak.melding.model.asErrorList
import no.nav.emottak.util.crypto.DecryptionException
import no.nav.emottak.util.crypto.Dekryptering
import no.nav.emottak.util.marker

private val dekryptering = Dekryptering()
class DekrypteringProcessor(override val ebMSMessage: EbmsPayloadMessage) : Processor(ebMSMessage) {
    override fun process() {
        // TODO om payload ikke er kryptert
        var payloadReference = "Ukjent"
        try {
            ebMSMessage.attachments.forEach {
                payloadReference = it.contentId
                dekryptering.dekrypter(it.payload, false)
            }
        } catch (e: DecryptionException) {
            log.error(ebMSMessage.messageHeader.marker(), "Feil ved dekryptering av payload $payloadReference", e)
            val error = ErrorCode.SECURITY_FAILURE.createEbxmlError(payloadReference)
            listOf(error).asErrorList()
            // TODO Skal resultere i retur av EbMS errorList til avsender?
        }
    }
}
