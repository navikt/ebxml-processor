package no.nav.emottak.ebms.processing

import no.nav.emottak.ebms.model.EbMSDocument
import no.nav.emottak.ebms.xml.EbMSSigning
import no.nav.emottak.util.signatur.SignatureException
import org.apache.xml.security.exceptions.XMLSecurityException
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader

private val ebMSSigning = EbMSSigning()

fun EbMSDocument.signer(messageHeader: MessageHeader): EbMSDocument {
    try {
        //TODO Do something with signed document
        ebMSSigning.sign(this.dokument, messageHeader, this.attachments)
        return this
    } catch (e: XMLSecurityException) {
        throw SignatureException("Signering av ebms envelope med attachments feilet", e)
    }
}

