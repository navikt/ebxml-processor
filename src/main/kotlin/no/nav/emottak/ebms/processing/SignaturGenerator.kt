package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.xml.EbMSSigning
import no.nav.emottak.util.marker
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.exceptions.XMLSecurityException
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.slf4j.LoggerFactory

private val ebMSSigning = EbMSSigning()
val log = LoggerFactory.getLogger("no.nav.emottak.ebms.processing")
fun EbMSDocument.signer(messageHeader: MessageHeader): EbMSDocument {
    try {
        //TODO Do something with signed document
        ebMSSigning.sign(this.dokument, messageHeader, this.attachments)
        return this
    } catch (e: XMLSecurityException) {
        log.error(messageHeader.marker(), "Signering av ebms envelope feilet", e)
        throw SignatureException("Signering av ebms envelope feilet", e)
    }
}
