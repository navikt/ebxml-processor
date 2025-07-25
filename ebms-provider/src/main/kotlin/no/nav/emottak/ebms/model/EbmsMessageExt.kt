package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.log
import no.nav.emottak.util.marker

fun EbmsMessage.validateSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, if (this is PayloadMessage) listOf(this.payload) else listOf())
    log.info(this.marker(), "Signatur OK")
}
