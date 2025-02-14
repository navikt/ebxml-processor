package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.xml.getDocumentBuilder
import no.nav.emottak.message.xml.marshal
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment
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
    abstract val refToMessageId: String?
    abstract val dokument: Document?
    val mottatt: Instant = Instant.now()
    open fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader())
    }

    open fun createFail(errorList: List<Feil>): EbmsFail {
        return EbmsFail(
            requestId,
            Uuid.random().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.replyTo(
                service = EbXMLConstants.EBMS_SERVICE_URI,
                action = EbXMLConstants.MESSAGE_ERROR_ACTION
            ),
            errorList
        )
    }
}

fun EbmsMessage.createAcknowledgementJaxB(): org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Acknowledgment {
    val acknowledgment = Acknowledgment()
    acknowledgment.id = "ACK_ID" // Identifier for Acknowledgment elementet, IKKE message ID (ebms spec 2.3.7)
    acknowledgment.version = "2.0"
    acknowledgment.isMustUnderstand = true // Alltid
    acknowledgment.actor = "http://schemas.xmlsoap.org/soap/actor/next"
    acknowledgment.timestamp = Date.from(this.mottatt)
    acknowledgment.refToMessageId = this.messageId
    acknowledgment.from = From().apply {
        this.partyId.addAll(
            this@createAcknowledgementJaxB.addressing.from.partyId.map {
                PartyId().apply {
                    this.value = it.value
                    this.type = it.type
                }
            }
        )
        this.role = this@createAcknowledgementJaxB.addressing.from.role
    }
    return acknowledgment
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.createMessageHeader(newAddressing: Addressing = this.addressing, withAcknowledgmentElement: Boolean = false): Header {
    val messageData = MessageData().apply {
        this.messageId = Uuid.random().toString()
        this.refToMessageId = this@createMessageHeader.refToMessageId
        this.timestamp = Date()
    }
    val from = From().apply {
        this.role = this@createMessageHeader.addressing.from.role
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
    // TODO SyncReply should not be present for SMTP messages
    val syncReply = SyncReply().apply {
        this.actor = "http://schemas.xmlsoap.org/soap/actor/next"
        this.isMustUnderstand = true
        this.version = "2.0"
    }
    val messageHeader = MessageHeader().apply {
        this.from = from
        this.to = to
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
    }

    return Header().apply {
        this.any.addAll(
            listOf(messageHeader, syncReply)
        )
        if (withAcknowledgmentElement) this.any.add(createAcknowledgementJaxB())
    }
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.toEbmsMessageDetails(): EbmsMessageDetails {
    return EbmsMessageDetails(
        EbmsMessageDetails.convertStringToUUIDOrGenerateNew(this.requestId),
        cpaId,
        conversationId,
        messageId,
        refToMessageId,
        EbmsMessageDetails.serializePartyId(addressing.from.partyId),
        addressing.from.role,
        EbmsMessageDetails.serializePartyId(addressing.to.partyId),
        addressing.to.role,
        addressing.service,
        addressing.action
    )
}

@OptIn(ExperimentalUuidApi::class)
fun EbmsMessage.createEbmsDocument(ebxmlDokument: Header, payload: EbmsAttachment? = null): EbMSDocument {
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
    val dokument = getDocumentBuilder().parse(InputSource(StringReader(marshal(envelope))))
    val payloads = if (payload != null) listOf(EbmsAttachment(payload.bytes, payload.contentType, payload.contentId)) else listOf()
    return EbMSDocument(
        requestId,
        dokument,
        payloads
    )
}
