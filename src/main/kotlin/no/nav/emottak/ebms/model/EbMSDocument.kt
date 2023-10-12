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

import no.nav.emottak.ebms.xml.xmlMarshaller
import no.nav.emottak.util.marker
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.time.LocalDateTime

val log = LoggerFactory.getLogger("no.nav.emottak.ebms.model")
data class EbMSDocument(val conversationId: String, val dokument: Document, val attachments: List<EbMSAttachment>){
    fun test() {

    }
}


fun EbMSDocument.buildEbmMessage(): EbMSBaseMessage {
    val envelope: Envelope = xmlMarshaller.unmarshal( this.dokument)
    val header = envelope.header
    return if (header.acknowledgment() != null) {
        log.info(header.messageHeader().marker(), "Mottak melding av type Acknowledgment")
        EbmsAcknowledgment(header.messageHeader(), header.acknowledgment()!!, this.dokument)
    } else if (header.errorList() != null) {
        log.info(header.messageHeader().marker(), "Mottak melding av type ErrorList")
        EbMSMessageError(header.messageHeader(), header.errorList()!!, this.dokument)
    } else {
        log.info(header.messageHeader().marker(), "Mottak melding av type payload")
        EbMSPayloadMessage(this.dokument,header.messageHeader(),header.ackRequested(),this.attachments, LocalDateTime.now())
    }
}