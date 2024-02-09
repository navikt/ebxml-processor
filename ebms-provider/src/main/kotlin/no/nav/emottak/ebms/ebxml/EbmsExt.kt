package no.nav.emottak.ebms.ebxml

import no.nav.emottak.ebms.validation.MimeValidationException
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Manifest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header

// TODO kan sikkert flytte alt dette til der det brukes.

fun Envelope.getConversationId(): String {
    val header = this.header?.any?.get(0) ?: throw MimeValidationException("Invalid Message Header")
    if (header is MessageHeader) {
        return header.conversationId
    } else {
        throw RuntimeException("Kunne ikke finne conversation ID")
    }
}

fun Envelope.getAttachmentId(): String { // TODO: egentlig kan vel det være n+1 attachments
    val manifest = this.body.any?.find { it is Manifest } as Manifest
    return manifest.reference.map { it.href }
        .first().replace("cid:", ""); // quickndirty
}

fun Header.messageHeader(): MessageHeader {
    return this.any!!.filterIsInstance<MessageHeader>().first()
}

fun Header.ackRequested(): AckRequested? {
    return this.any!!.filterIsInstance<AckRequested>().firstOrNull()
}

fun Header.acknowledgment(): Acknowledgment? {
    return this.any!!.filterIsInstance<Acknowledgment>().firstOrNull()
}

fun Header.errorList(): ErrorList? {
    return this.any!!.filterIsInstance<ErrorList>().firstOrNull()
}

// fun .getActor(): String {
//    return this.any!!.filterIsInstance<AckRequested>()
//        .filter{ isNotBlank(it.actor) }.map { it.actor }.filterNotNull().first()
// }

fun MessageHeader.getAckRequestedSigned(): Boolean? {
    return this.any!!.filterIsInstance<AckRequested>().find { it.isSigned }?.isSigned // Kotlin quirk. Med isSigned menes at en signed Ack er ønsket
}
