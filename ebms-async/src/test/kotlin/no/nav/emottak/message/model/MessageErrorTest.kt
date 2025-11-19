package no.nav.emottak.message.model

import no.nav.emottak.ebms.async.processing.createPayloadMessage
import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.ebxml.ackRequested
import no.nav.emottak.message.ebxml.errorList
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.xml.xmlMarshaller
import org.junit.jupiter.api.Test
import org.xmlsoap.schemas.soap.envelope.Envelope
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageErrorTest {

    @Test
    fun `MessageError from PayloadMessage has correct values set`() {
        val payloadMessage = createPayloadMessage()
        val failureList = listOf(
            Feil(
                ErrorCode.SECURITY_FAILURE,
                "Test error message"
            )
        )

        val messageError = payloadMessage.createMessageError(failureList)
        assertEquals(payloadMessage.messageId, messageError.refToMessageId, "RefToMessageId should match original messageId")
        assertEquals(payloadMessage.conversationId, messageError.conversationId, "ConversationId should match original conversationId")
        assertEquals(payloadMessage.cpaId, messageError.cpaId, "CPAId should match original CPAId")

        val messageErrorDocument = messageError.toEbmsDokument()
        assertEquals(DocumentType.MESSAGE_ERROR, messageErrorDocument.documentType(), "Document type should be MESSAGE_ERROR")

        val header = (xmlMarshaller.unmarshal(messageErrorDocument.document) as Envelope).header!!
        assertEquals(null, header.ackRequested(), "AckRequested should not be present in MessageError message")

        val messageHeader = header.messageHeader()
        assertEquals(payloadMessage.cpaId, messageHeader.cpaId, "CPAId in MessageHeader should match original CPAId")
        assertEquals(messageError.messageId, messageHeader.messageData.messageId, "MessageId in MessageHeader should match MessageError messageId")
        assertEquals(payloadMessage.conversationId, messageHeader.conversationId, "ConversationId in MessageHeader should match original ConversationId")
        assertEquals(null, messageHeader.duplicateElimination, "DuplicateElimination should not be present in MessageError message")
        assertEquals(EbXMLConstants.EBMS_SERVICE_URI, messageHeader.service.value, "Service URI should match MessageError service URI")
        assertEquals(EbXMLConstants.MESSAGE_ERROR_ACTION, messageHeader.action, "Action should match MessageError action")

        val messageErrorElement = header.errorList()
        assertNotNull(messageErrorElement, "Acknowledgment element should be present in header")
        assertEquals("2.0", messageErrorElement.version, "Version in Acknowledgment element should be '2.0'")
        assertEquals(true, messageErrorElement.isMustUnderstand, "MustUnderstand in Acknowledgment element should be true")
        assertEquals(1, messageErrorElement.error.size, "There should be one error in the ErrorList")
        assertEquals(failureList.first().code.value, messageErrorElement.error[0].errorCode, "ErrorCode in ErrorList should match the one in failureList")
        assertEquals(failureList.first().descriptionText, messageErrorElement.error[0].description?.value, "ErrorCode in ErrorList should match the one in failureList")
    }
}
