package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.message.model.Acknowledgment
import no.nav.emottak.message.model.EbmsFail
import no.nav.emottak.message.model.EbmsMessage
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.log

fun EbmsFail.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
    no.nav.emottak.message.model.log.info("Signatur OK")
}

fun Acknowledgment.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
    log.info("Signatur OK")
}

fun PayloadMessage.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, listOf(this.payload))
    no.nav.emottak.message.model.log.info("Signatur OK")
}

fun EbmsMessage.sjekkSignature(signatureDetails: SignatureDetails) {
    SignaturValidator.validate(signatureDetails, this.dokument!!, if (this is PayloadMessage) listOf(this.payload) else listOf())
    log.info("Signatur OK")
}
