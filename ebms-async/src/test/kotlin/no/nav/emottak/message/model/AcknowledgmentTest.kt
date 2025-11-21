package no.nav.emottak.message.model

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.processing.createPayloadMessage
import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.ebxml.ackRequested
import no.nav.emottak.message.ebxml.acknowledgment
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.xml.createDocument
import no.nav.emottak.message.xml.xmlMarshaller
import org.apache.xml.security.utils.Constants
import org.junit.jupiter.api.Test
import org.xmlsoap.schemas.soap.envelope.Envelope
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AcknowledgmentTest {

    @Test
    fun `Acknowledgment from PayloadMessage has correct values set`() {
        val payloadMessage = createPayloadMessage(
            document = runBlocking {
                this::class.java.classLoader
                    .getResourceAsStream("signaltest/payloadmessage.xml")!!.readAllBytes().createDocument()
            }
        )
        val signatureReferences = payloadMessage.document!!.getElementsByTagNameNS(
            Constants.SignatureSpecNS,
            EbXMLConstants.XMLDSIG_TAG_REFERENCE
        )
        assertEquals(2, signatureReferences.length, "There should be 2 Reference elements in the original PayloadMessage document")

        val acknowledgment = payloadMessage.createAcknowledgment()
        assertEquals(payloadMessage.messageId, acknowledgment.refToMessageId, "RefToMessageId should match original messageId")
        assertEquals(payloadMessage.conversationId, acknowledgment.conversationId, "ConversationId should match original conversationId")
        assertEquals(payloadMessage.cpaId, acknowledgment.cpaId, "CPAId should match original CPAId")

        val acknowledgmentDocument = acknowledgment.toEbmsDokument()
        assertEquals(DocumentType.ACKNOWLEDGMENT, acknowledgmentDocument.documentType(), "Document type should be ACKNOWLEDGMENT")

        val header = (xmlMarshaller.unmarshal(acknowledgmentDocument.document) as Envelope).header!!
        assertEquals(null, header.ackRequested(), "AckRequested should not be present in acknowledgment message")

        val messageHeader = header.messageHeader()
        assertEquals(payloadMessage.cpaId, messageHeader.cpaId, "CPAId in MessageHeader should match original CPAId")
        assertEquals(acknowledgment.messageId, messageHeader.messageData.messageId, "MessageId in MessageHeader should match acknowledgment messageId")
        assertEquals(null, messageHeader.messageData.refToMessageId, "RefToMessageId in MessageHeader should not be set for acknowledgment message")
        assertEquals(payloadMessage.conversationId, messageHeader.conversationId, "ConversationId in MessageHeader should match original ConversationId")
        assertEquals(null, messageHeader.duplicateElimination, "DuplicateElimination should not be present in acknowledgment message")
        assertEquals(EbXMLConstants.EBMS_SERVICE_URI, messageHeader.service.value, "Service URI should match acknowledgment service URI")
        assertEquals(EbXMLConstants.ACKNOWLEDGMENT_ACTION, messageHeader.action, "Action should match acknowledgment action")

        val acknowledgmentElement = header.acknowledgment()
        assertNotNull(acknowledgmentElement, "Acknowledgment element should be present in header")
        assertEquals(payloadMessage.messageId, acknowledgmentElement.refToMessageId, "RefToMessageId in Acknowledgment element should match original messageId")
        assertEquals("2.0", acknowledgmentElement.version, "Version in Acknowledgment element should be '2.0'")
        assertEquals(true, acknowledgmentElement.isMustUnderstand, "MustUnderstand in Acknowledgment element should be true")
        assertNotNull(acknowledgmentElement.timestamp, "Timestamp in Acknowledgment element should not be null")
        assertEquals(2, acknowledgmentElement.reference.size, "There should be 2 Reference elements in Acknowledgment element")

        for (i in acknowledgmentElement.reference.indices) {
            assertEquals(signatureReferences.item(i).attributes.getNamedItem("URI").nodeValue, acknowledgmentElement.reference[i].uri, "URI of Reference $i should match original")
        }
    }
}
