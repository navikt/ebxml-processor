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
import org.xmlsoap.schemas.soap.envelope.Envelope

data class EbMSDocument(val conversationId: String, val dokument: ByteArray, val attachments: List<EbMSAttachment>)


fun EbMSDocument.buildEbmMessage(): EbMSMessage? {
    println(String( this.dokument))
    val envelope = xmlMarshaller.unmarshal( String( this.dokument), Envelope::class.java)
    return EbMSMessage(envelope.header(),envelope.ackRequested(),this.attachments )
}