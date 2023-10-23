package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.xml.EbMSSigning
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.util.marker
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.exceptions.XMLSecurityException
import org.slf4j.LoggerFactory

private val ebMSSigning = EbMSSigning()
val log = LoggerFactory.getLogger("no.nav.emottak.ebms.processing")
fun EbMSDocument.signer(signatureDetails: SignatureDetails): EbMSDocument {
    try {
        ebMSSigning.sign(this, signatureDetails)
        return this
    } catch (e: XMLSecurityException) {
        log.error(this.messageHeader().marker(), "Signering av ebms envelope feilet", e)
        throw SignatureException("Signering av ebms envelope feilet", e)
    }
}

