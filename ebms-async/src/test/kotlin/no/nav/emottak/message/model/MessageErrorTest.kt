package no.nav.emottak.message.model

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.processing.createPayloadMessage
import no.nav.emottak.message.ebxml.EbXMLConstants
import no.nav.emottak.message.ebxml.EbXMLConstants.OASIS_EBXML_MSG_HEADER_XSD_NS_URI
import no.nav.emottak.message.ebxml.ackRequested
import no.nav.emottak.message.ebxml.errorList
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.xml.createDocument
import no.nav.emottak.message.xml.xmlMarshaller
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
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
        val expectedDescription = """
        {"MSH-system":"NAV EBMS","MSH-versjon":"1.0.0"}
        """.trimIndent()
        assertEquals(1, messageHeader.description.size, "MessageHeader should contain 1 Description")
        assertEquals("NO", messageHeader.description[0].lang, "Description lang in MessageHeader should be as expected")
        assertEquals(expectedDescription, messageHeader.description[0].value, "Description value in MessageHeader should be as expected")

        val messageErrorElement = header.errorList()
        assertNotNull(messageErrorElement, "Acknowledgment element should be present in header")
        assertEquals("2.0", messageErrorElement.version, "Version in Acknowledgment element should be '2.0'")
        assertEquals(true, messageErrorElement.isMustUnderstand, "MustUnderstand in Acknowledgment element should be true")
        assertEquals(1, messageErrorElement.error.size, "There should be one error in the ErrorList")
        assertEquals(failureList.first().code.value, messageErrorElement.error[0].errorCode, "ErrorCode in ErrorList should match the one in failureList")
        assertEquals(failureList.first().descriptionText, messageErrorElement.error[0].description?.value, "ErrorCode in ErrorList should match the one in failureList")
    }

    @Test
    fun `Incoming MessageError with RefToMessageId is transformed with the given RefToMessageId`() {
        val document = readMessageErrorDocument()

        val messageError = EbmsDocument("requestId", document, emptyList()).transform() as MessageError

        assertEquals("autotest_tc_2_2_15", messageError.refToMessageId)
    }

    @Test
    fun `Incoming MessageError without RefToMessageId is transformed with placeholder RefToMessageId`() {
        val document = readMessageErrorDocument()
        val refToMessageIdElements = document.getElementsByTagNameNS(OASIS_EBXML_MSG_HEADER_XSD_NS_URI, "RefToMessageId")
        assertEquals(1, refToMessageIdElements.length)
        val refToMessageIdElement = refToMessageIdElements.item(0)
        refToMessageIdElement.parentNode.removeChild(refToMessageIdElement)

        val messageError = EbmsDocument("requestId", document, emptyList()).transform() as MessageError

        assertEquals(REF_TO_MESSAGE_ID_NOT_SET, messageError.refToMessageId)
        assertEquals("20140607-214220-84523@dev.ebxml.nav.no", messageError.messageId)
        assertEquals("unknown", messageError.cpaId)
        assertEquals("20140607-214220-751-0", messageError.conversationId)
        assertEquals(1, messageError.feil.size)
    }

    private fun readMessageErrorDocument(): Document = runBlocking {
        this@MessageErrorTest::class.java.classLoader
            .getResourceAsStream("signaltest/messageerror.xml")!!.readAllBytes().createDocument()
    }
}
