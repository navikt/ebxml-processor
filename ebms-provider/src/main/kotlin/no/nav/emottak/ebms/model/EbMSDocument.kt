/**
 * Copyright 2011 Clockwork
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.nav.emottak.ebms.model

import no.nav.emottak.ebms.getPublicSigningDetails
import no.nav.emottak.ebms.processing.SignatursjekkProcessor
import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.melding.model.SignatureDetails
import no.nav.emottak.util.marker
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.lang.RuntimeException
import java.time.LocalDateTime

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.model")
data class EbMSDocument(val messageId: String, val dokument: Document, val attachments: List<EbMSAttachment>) {
    fun dokumentType(): DokumentType {
        if (attachments.size > 0) return DokumentType.PAYLOAD
        if (dokument.getElementsByTagName("Acknowledgment").item(0) != null) return DokumentType.ACKNOWLEDGMENT
        if (dokument.getElementsByTagName("ErrorList").item(0)) return DokumentType.FAIL
        throw RuntimeException("Unrecognized dokument type")

    }
    fun messageHeader():MessageHeader {
         val node: Node =this.dokument.getElementsByTagName("eb:MessageHeader").item(0)
         return xmlMarshaller.unmarshal(node)
    }

}

enum class DokumentType {
    PAYLOAD, ACKNOWLEDGMENT,FAIL,STATUS,PING
}

fun EbMSDocument.sjekkSignature(signatureDetails: SignatureDetails) {
    SignatursjekkProcessor().validate(signatureDetails, this.dokument, this.attachments)
}

fun EbMSDocument.sjekkSignature() {
    SignatursjekkProcessor().validate(
        this.messageHeader().getPublicSigningDetails(),
        this.dokument,
        this.attachments)
}

fun EbMSDocument.buildEbmMessage(): EbMSBaseMessage {
    val envelope: Envelope = xmlMarshaller.unmarshal( this.dokument)
    val header = envelope.header
    return if (header.acknowledgment() != null) {
        log.info(header.messageHeader().marker(), "Mottak melding av type Acknowledgment")
        EbMSAckMessage(header.messageHeader(), header.acknowledgment()!!, this.dokument)
    } else if (header.errorList() != null) {
        log.info(header.messageHeader().marker(), "Mottak melding av type ErrorList")
        EbMSErrorMessage(header.messageHeader(), header.errorList()!!, this.dokument)
    } else {
        log.info(header.messageHeader().marker(), "Mottak melding av type payload")
        EbMSPayloadMessage(this.dokument,header.messageHeader(),header.ackRequested(),this.attachments, LocalDateTime.now())
    }
}

fun EbMSDocument.checkSignature(signatureDetails: SignatureDetails, dokument: Document, attachments: List<EbMSAttachment>) {
    SignatursjekkProcessor().validate(signatureDetails, dokument, attachments)
}

fun EbMSDocument.sendResponse(messageHeader: MessageHeader) {
    log.info(messageHeader.marker(), "TODO return response message")
}
fun EbMSDocument.sendErrorResponse(messageHeader: MessageHeader) {
    log.error(messageHeader.marker(), "TODO return response message")
}