package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.util.marker
import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.log

fun EbmsMessage.sjekkSignature(signatureDetails: SignatureDetails) {
    if (this is PayloadMessage) {
        log.debug(this.marker(), "Debugging signature bug: ${this.payload}")
        log.debug(this.marker(), "Debugging signature bug: ${String(this.payload.bytes)}")
    }
    SignaturValidator.validate(signatureDetails, this.dokument!!, if (this is PayloadMessage) listOf(this.payload) else listOf())
    log.info(this.marker(), "Signatur OK")
}
