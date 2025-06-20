package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.xml.ebMSSigning
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.util.signatur.SignatureException

fun EbMSDocument.signer(signatureDetails: SignatureDetails): EbMSDocument =
    try {
        ebMSSigning.sign(this, signatureDetails)
        this
    } catch (e: Exception) {
        throw SignatureException("Error signing outgoing ebXML envelope", e)
    }
