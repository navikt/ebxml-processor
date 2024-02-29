package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.validation.SignaturValidator
import no.nav.emottak.ebms.xml.getDocumentBuilder
import no.nav.emottak.ebms.xml.marshal
import no.nav.emottak.melding.model.Addressing
import no.nav.emottak.melding.model.Feil
import no.nav.emottak.melding.model.SignatureDetails
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Manifest
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Reference
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xmlsoap.schemas.soap.envelope.Body
import org.xmlsoap.schemas.soap.envelope.Envelope
import org.xmlsoap.schemas.soap.envelope.Header
import java.io.StringReader
import java.util.Date
import java.util.UUID
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.From
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageData
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.PartyId
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.Service
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.SyncReply
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.To

open class EbmsMessage(
    open val requestId: String,
    open val messageId: String,
    open val conversationId: String,
    open val cpaId: String,
    open val addressing: Addressing,
    val dokument: Document? = null,
    open val refToMessageId: String? = null
) {

    open fun sjekkSignature(signatureDetails: SignatureDetails) {
        SignaturValidator.validate(signatureDetails, this.dokument!!, listOf())
        log.info("Signatur OK")
    }

    open fun toEbmsDokument(): EbMSDocument {
        return createEbmsDocument(createMessageHeader(), null)
    }

    open fun createFail(errorList: List<Feil>): EbmsFail {
        return EbmsFail(
            requestId,
            UUID.randomUUID().toString(),
            this.messageId,
            this.conversationId,
            this.cpaId,
            this.addressing.copy(to = addressing.from, from = addressing.to),
            errorList
        )
    }
}


fun EbmsMessage.createMessageHeader(newAddressing:Addressing = this.addressing ): Header {
    val messageData = MessageData().apply {
        this.messageId = UUID.randomUUID().toString()
        this.refToMessageId = this.messageId
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
    val syncReply = SyncReply().apply {
        this.actor = "http://schemas.xmlsoap.org/soap/actor/next"
        this.isMustUnderstand = true
        this.version = "2.0"
    }
    val messageHeader = MessageHeader().apply {
        this.from = from
        this.to = to
        this.cpaId = this.cpaId
        this.conversationId = this.conversationId
        this.service = Service().apply {
            this.value = this@createMessageHeader.addressing.service
            this.type = "string"
        }
        this.action = this@createMessageHeader.addressing.action
        this.messageData = messageData
    }

    return Header().apply {
        this.any.addAll(
            listOf(messageHeader, syncReply)
        )
    }
}


fun createEbmsDocument(ebxmlDokument: Header, payload: Payload? = null): EbMSDocument {
    val envelope = Envelope()
    val attachmentUid = UUID.randomUUID().toString()
    envelope.header = ebxmlDokument

    envelope.body = Body().apply {
        this.any.add(
            Manifest().apply {
                this.reference.add(
                    Reference().apply {
                        this.href = "cid:" + attachmentUid
                        this.type = "simple"
                    }
                )
            }
        )
    }
    val dokument = getDocumentBuilder().parse(InputSource(StringReader(marshal(envelope))))
    val payloads = if (payload != null) listOf(EbmsAttachment(payload.payload, payload.contentType, payload.contentId)) else listOf()
    return EbMSDocument(
        UUID.randomUUID().toString(),
        dokument,
        payloads
    )
}
