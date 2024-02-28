package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.SignatureDetails
import org.w3c.dom.Document
import java.util.UUID

open class EbmsMessage(
    open val requestId: String,
    open val messageId: String,
    open val conversationId: String,
    open val cpaId: String,
    open val addressing: Addressing,
    val dokument: Document? = null,
    open val refToMessageId: String? = null
) {

    open fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
        no.nav.emottak.ebms.model.log.info("Signatur OK")
    }

    open fun createFail(errorList: List<Feil>): EbmsFail {
        return EbmsFail(
            requestId,
            UUID.randomUUID().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.copy(to = addressing.from, from = addressing.to),
            errorList
        )
    }
}
