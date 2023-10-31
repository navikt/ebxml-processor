package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSErrorUtil
import no.nav.emottak.ebms.model.EbMSPayloadMessage
import no.nav.emottak.util.crypto.DecryptionException
import no.nav.emottak.util.crypto.Dekryptering
import no.nav.emottak.util.marker


private val dekryptering = Dekryptering()
class DekrypteringProcessor(override val ebMSMessage: EbMSPayloadMessage): Processor(ebMSMessage) {
    override fun process() {
        //TODO om payload ikke er kryptert
        var payloadReference = "Ukjent"
        try {
            ebMSMessage.attachments.forEach {
                payloadReference = it.contentId
                dekryptering.dekrypter(it.dataSource, false)
            }
        } catch (e: DecryptionException) {
            log.error(ebMSMessage.messageHeader.marker(), "Feil ved dekryptering av payload $payloadReference", e)
            val error = EbMSErrorUtil.createError(EbMSErrorUtil.Code.SECURITY_FAILURE, payloadReference)
            EbMSErrorUtil.createErrorList(listOf(error))
            //TODO Skal resultere i retur av EbMS errorList til avsender?
        }
    }
}