package no.nav.emottak.message.ebxml

import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Error
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.ErrorList
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Manifest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header

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

/**
 * Some non-conformant senders emit the `errorCode` attribute without the expected `eb` namespace
 * qualification. JAXB then leaves [Error.errorCode] null and instead captures the value in
 * [Error.otherAttributes] under a QName with no namespace. Fall back to that when needed.
 */
fun Error.effectiveErrorCode(): String? {
    return this.errorCode ?: this.otherAttributes?.entries?.firstOrNull { it.key.localPart == "errorCode" }?.value
}

fun MessageHeader.getAckRequestedSigned(): Boolean? {
    return this.any!!.filterIsInstance<AckRequested>().find { it.isSigned }?.isSigned // Kotlin quirk. Med isSigned menes at en signed Ack er ønsket
}
