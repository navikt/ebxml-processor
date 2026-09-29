package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.xml.ebmsSigning
import no.nav.emottak.message.exception.SignatureValidationException
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.SignatureDetails

fun EbmsDocument.signer(signatureDetails: SignatureDetails): EbmsDocument =
    try {
        ebmsSigning.sign(this, signatureDetails)
        this
    } catch (e: Exception) {
        throw SignatureValidationException("Error signing outgoing ebXML envelope", e)
    }
