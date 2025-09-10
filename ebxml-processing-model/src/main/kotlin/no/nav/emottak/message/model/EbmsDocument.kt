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
package no.nav.emottak.message.model

import no.nav.emottak.message.ebxml.EbXMLConstants.OASIS_EBXML_MSG_HEADER_TAG
import no.nav.emottak.message.ebxml.EbXMLConstants.OASIS_EBXML_MSG_HEADER_XSD_NS_URI
import no.nav.emottak.message.ebxml.acknowledgment
import no.nav.emottak.message.ebxml.addressing
import no.nav.emottak.message.ebxml.errorList
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.xml.xmlMarshaller
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xmlsoap.schemas.soap.envelope.Envelope

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.model")

data class EbmsDocument(val requestId: String, val document: Document, val attachments: List<Payload>) {

    private val envelope = lazy { xmlMarshaller.unmarshal(this.document) as Envelope }

    fun documentType(): DocumentType = document.documentType()

    fun messageHeader(): MessageHeader {
        val node: Node =
            this.document.getElementsByTagNameNS(OASIS_EBXML_MSG_HEADER_XSD_NS_URI, OASIS_EBXML_MSG_HEADER_TAG).item(0)
        return xmlMarshaller.unmarshal(node)
    }

    fun transform(): EbmsMessage {
        val header = envelope.value.header!!
        val messageHeader = header.messageHeader()

        return when (documentType()) {
            DocumentType.PAYLOAD -> PayloadMessage(
                requestId,
                messageHeader.messageData.messageId,
                messageHeader.conversationId,
                messageHeader.cpaId!!,
                messageHeader.addressing(),
                attachments.first(),
                document,
                messageHeader.messageData.refToMessageId,
                messageHeader.messageData.timestamp.toInstant(),
                messageHeader.duplicateElimination != null
            )

            DocumentType.MESSAGE_ERROR -> {
                val errorList = header.errorList()!!.error.map {
                    Feil(ErrorCode.fromString(it.errorCode), it.description!!.value!!)
                }.toList()
                MessageError(
                    requestId,
                    messageHeader.messageData.messageId,
                    messageHeader.messageData.refToMessageId!!,
                    messageHeader.conversationId,
                    messageHeader.cpaId!!,
                    messageHeader.addressing(isRoleApplicable = false),
                    errorList,
                    document,
                    messageHeader.messageData.timestamp.toInstant()
                )
            }

            DocumentType.ACKNOWLEDGMENT -> {
                Acknowledgment(
                    requestId,
                    messageHeader.messageData.messageId,
                    header.acknowledgment()!!.refToMessageId,
                    messageHeader.conversationId,
                    messageHeader.cpaId!!,
                    messageHeader.addressing(isRoleApplicable = false),
                    document,
                    messageHeader.messageData.timestamp.toInstant()
                )
            }

            else -> throw RuntimeException("Unrecognized message type ${documentType()}")
        }
    }
}

enum class DocumentType {
    PAYLOAD, ACKNOWLEDGMENT, MESSAGE_ERROR, STATUS, PING
}

fun Document.documentType(): DocumentType {
    if (this.getElementsByTagNameNS(OASIS_EBXML_MSG_HEADER_XSD_NS_URI, "MessageHeader").length == 0) {
        throw RuntimeException("Message does not contain ebXML message header")
    }

    return if (this.getElementsByTagNameNS(OASIS_EBXML_MSG_HEADER_XSD_NS_URI, "Acknowledgment")
        .item(0) != null
    ) {
        DocumentType.ACKNOWLEDGMENT
    } else if (this.getElementsByTagNameNS(OASIS_EBXML_MSG_HEADER_XSD_NS_URI, "ErrorList")
        .item(0) != null
    ) {
        DocumentType.MESSAGE_ERROR
    } else {
        DocumentType.PAYLOAD
    }
}
