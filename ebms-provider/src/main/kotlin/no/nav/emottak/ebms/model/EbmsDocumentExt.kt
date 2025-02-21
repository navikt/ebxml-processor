package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.xml.ebMSSigning
import no.nav.emottak.message.model.EbMSDocument
import no.nav.emottak.message.model.SignatureDetails
import no.nav.emottak.message.model.log
import no.nav.emottak.util.signatur.SignatureException
import no.nav.emottak.utils.marker

fun EbMSDocument.signer(signatureDetails: SignatureDetails): EbMSDocument {
    try {
        ebMSSigning.sign(this, signatureDetails)
        return this
    } catch (e: Exception) {
        log.error(this.messageHeader().marker(), "Signering av ebms envelope feilet", e)
        throw SignatureException("Signering av ebms envelope feilet", e)
    }
}
