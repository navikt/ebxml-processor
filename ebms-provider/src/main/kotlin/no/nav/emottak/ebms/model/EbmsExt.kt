package no.nav.emottak.ebms.model

import org.apache.commons.lang3.StringUtils.isNotBlank
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.*
import org.xmlsoap.schemas.soap.envelope.Envelope

// TODO kan sikkert flytte alt dette til der det brukes.

fun Envelope.getConversationId() : String {
    val header = this.header.any[0]
    if (header is MessageHeader)
        return header.conversationId
    else
        throw RuntimeException("Kunne ikke finne conversation ID");
}

fun Envelope.getAttachmentId() : String { // TODO: egentlig kan vel det være n+1 attachments
    val manifest = this.body.any.find { it is Manifest } as Manifest
    return manifest.reference.map { it.href }
        .first().replace("cid:", ""); // quickndirty
}

fun Envelope.getFrom (): From {
    return (this.header.any.find { it is MessageHeader } as MessageHeader).from
}

fun Envelope.getVersion(): String {
    return this.header.any.filterIsInstance<MessageHeader>()
        .stream().filter { isNotBlank(it.version) }
        .map { it.version }.findFirst().get()
}

fun Envelope.getMessageId(): String {
    return this.header.any.filterIsInstance<MessageData>()
        .stream().filter { isNotBlank(it.messageId) }
        .map { it.messageId }.findFirst().get()
}

fun Envelope.header(): MessageHeader {
    return this.header.any.filterIsInstance<MessageHeader>().first()
}

fun Envelope.ackRequested() : AckRequested? {
    return this.header.any.filterIsInstance<AckRequested>().first()
}

fun Envelope.getActor(): String {
    return this.header.any.filterIsInstance<AckRequested>().stream()
        .filter{ isNotBlank(it.actor) }.map { it.actor }.findFirst().get()
}

fun Envelope.getAckRequestedSigned(): Boolean {
    return this.header.any.filterIsInstance<AckRequested>().stream()
        .anyMatch { it.isSigned } // Kotlin quirk. Med isSigned menes at en signed Ack er ønsket
}
