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
import org.w3c.dom.Document
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.time.LocalDateTime

data class EbMSDocument(val conversationId: String, val dokument: Document, val attachments: List<EbMSAttachment>){
    fun test() {

    }
}


fun EbMSDocument.buildEbmMessage(): EbMSPayloadMessage {


    val envelope: Envelope = xmlMarshaller.unmarshal( this.dokument)
    return EbMSPayloadMessage(this.dokument,envelope.header(),envelope.ackRequested(),this.attachments, LocalDateTime.now())
}