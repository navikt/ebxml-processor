package no.nav.emottak.ebms.model

import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.w3c.dom.Document

interface EbmsBaseMessage {
    val messageHeader: MessageHeader
    val dokument: Document?
}
