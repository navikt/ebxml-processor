package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.message.xml.marshal
import no.nav.emottak.utils.common.model.Addressing
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.AckRequested
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Description
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.From
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Manifest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageData
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.PartyId
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Reference
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Service
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SyncReply
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.To
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header
import java.io.StringReader
import java.time.Instant
import java.util.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
abstract class EbmsMessage {
    abstract val requestId: String
    abstract val messageId: String
    abstract val conversationId: String
    abstract val cpaId: String
    abstract val addressing: Addressing
    abstract val description: List<Description>?
    abstract val refToMessageId: String?
    abstract val document: Document?
    abstract val sentAt: Instant?
    open fun toEbmsDokument(): EbmsDocument {
        return createEbmsDocument(createMessageHeader())
    }

    open fun createMessageError(errorList: List<Feil>): MessageError {
        return MessageError(
            requestId,
            Uuid.random().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.replyTo(
                service = EbXMLConstants.EBMS_SERVICE_URI,
                action = EbXMLConstants.MESSAGE_ERROR_ACTION
            ),
            this.description ?: emptyList(),
            errorList
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.createMessageHeader(
    newAddressing: Addressing = this.addressing,
    description: List<Description> = this.description ?: emptyList(),
    withSyncReplyElement: Boolean = false,
    withAckRequestedElement: Boolean = false,
    withDuplicateEliminationElement: Boolean = false
): Header {
    val messageData = MessageData().apply {
        this.messageId = this@createMessageHeader.messageId
        if (this@createMessageHeader !is Acknowledgment) {
            this.refToMessageId = this@createMessageHeader.refToMessageId
        }
        this.timestamp = Date()
    }
    val from = From().apply {
        this.role = newAddressing.from.role
        this.partyId.addAll(
            newAddressing.from.partyId.map {
                PartyId().apply {
                    this.type = it.type
                    this.value = it.value
                }
            }.toList()
        )
    }
    val to = To().apply {
        this.role = newAddressing.to.role
        this.partyId.addAll(
            newAddressing.to.partyId.map {
                PartyId().apply {
                    this.type = it.type
                    this.value = it.value
                }
            }.toList()
        )
    }
    val messageHeader = MessageHeader().apply {
        this.from = from
        this.to = to
        this.description.addAll(description)
        this.cpaId = this@createMessageHeader.cpaId
        this.conversationId = this@createMessageHeader.conversationId
        this.service = Service().apply {
            this.value = newAddressing.service
            this.type = "string"
        }
        this.isMustUnderstand = true
        this.version = "2.0"
        this.action = newAddressing.action
        this.messageData = messageData
        if (withDuplicateEliminationElement) this.duplicateElimination = ""
    }

    return Header().apply {
        this.any.add(messageHeader)
        if (withSyncReplyElement) this.any.add(createSyncReplyElement())
        if (withAckRequestedElement) this.any.add(createAckRequestedElement())
    }
}

private fun createSyncReplyElement() = SyncReply().apply {
    this.actor = "http://schemas.xmlsoap.org/soap/actor/next"
    this.isMustUnderstand = true
    this.version = "2.0"
}

private fun createAckRequestedElement() = AckRequested().apply {
    this.isSigned = true
    this.actor = "urn:oasis:names:tc:ebxml-msg:actor:toPartyMSH"
    this.isMustUnderstand = true
    this.version = "2.0"
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.createEbmsDocument(ebxmlDokument: Header, payload: EbmsAttachment? = null): EbmsDocument {
    val envelope = Envelope()
    val attachmentUid = Uuid.random().toString()
    envelope.header = ebxmlDokument

    envelope.body = Body().apply {
        if (payload != null) {
            this.any.add(
                Manifest().apply {
                    this.version = "2.0"
                    this.reference.add(
                        Reference().apply {
                            this.href = "cid:${payload.contentId}"
                            this.type = "simple"
                        }
                    )
                }
            )
        }
    }
    val document = getDocumentBuilder().parse(InputSource(StringReader(marshal(envelope))))
    val payloads = if (payload != null) listOf(EbmsAttachment(payload.bytes, payload.contentType, payload.contentId)) else listOf()
    return EbmsDocument(
        requestId,
        document,
        payloads
    )
}
