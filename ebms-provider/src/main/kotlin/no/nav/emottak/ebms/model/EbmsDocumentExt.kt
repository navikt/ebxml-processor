package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.xml.ebmsSigning
import no.nav.emottak.message.model.EbmsDocument
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.signatur.SignatureException

fun EbmsDocument.signer(signatureDetails: SignatureDetails): EbmsDocument =
    try {
        ebmsSigning.sign(this, signatureDetails)
        this
    } catch (e: Exception) {
        throw SignatureException("Error signing outgoing ebXML envelope", e)
    }
