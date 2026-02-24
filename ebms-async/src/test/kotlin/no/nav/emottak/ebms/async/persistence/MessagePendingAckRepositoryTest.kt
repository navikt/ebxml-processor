package no.nav.emottak.ebms.async.persistence

import no.nav.emottak.ebms.async.ebmsPostgres
import no.nav.emottak.ebms.async.persistence.repository.MessagePendingAckRepository
import no.nav.emottak.ebms.async.testConfiguration
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.xml.XmlMarshaller
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.testcontainers.containers.PostgreSQLContainer
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.sql.DriverManager
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class MessagePendingAckRepositoryTest {
    companion object {
        lateinit var messagePendingAckRepository: MessagePendingAckRepository
        lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            messagePendingAckRepository = MessagePendingAckRepository(ebmsProviderDb, 5.minutes, 2)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            ebmsProviderDbContainer.stop()
        }
    }

    @AfterEach
    fun cleanUpTable() {
        val jdbcUrl = ebmsProviderDbContainer.jdbcUrl
        val username = ebmsProviderDbContainer.username
        val password = ebmsProviderDbContainer.password

        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DELETE FROM message_pending_ack")
            }
        }
    }

    private val xmlMarshaller = XmlMarshaller()

    @Test
    fun `Write MessagePendingAck and read back`() {
        val requestId = Uuid.random()
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val messageId = messageHeader.messageData.messageId
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        messagePendingAckRepository.storeMessagePendingAck(requestId, messageHeader, payload.toByteArray(), emailList)

        // No message to be resent before 12 hours is passed
        var messages = messagePendingAckRepository.findMessagesToResend()
        Assertions.assertEquals(0, messages.size)

        // Read again but with specified cutoff, like it would be if the insert was more than 12 hours ago
        messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        Assertions.assertEquals(1, messages.size)

        Assertions.assertEquals(messageId, messages[0].messageId)
        Assertions.assertEquals(false, messages[0].ackReceived)
        Assertions.assertEquals(xmlMarshaller.marshal(messageHeader), xmlMarshaller.marshal(messages[0].messageHeader))
        Assertions.assertEquals(payload, String(messages[0].messageContent))
        Assertions.assertEquals(listOf(email1, email2), messages[0].emailAddressList)
        Assertions.assertTrue { messages[0].firstSent.isBefore(Instant.now()) }
        Assertions.assertTrue { messages[0].lastSent.isBefore(Instant.now()) }
        Assertions.assertEquals(0, messages[0].resentCount)
    }

    @Test
    fun `Verify markResent`() {
        val requestId = Uuid.random()
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        messagePendingAckRepository.storeMessagePendingAck(requestId, messageHeader, payload.toByteArray(), emailList)

        val afterCreation = Instant.now()
        var messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        Assertions.assertTrue { messages[0].lastSent.isBefore(afterCreation) }
        Assertions.assertEquals(0, messages[0].resentCount)

        // Mark that we have resent the message once.
        // This should update counter and last_sent timestamp
        messagePendingAckRepository.markResent(messages[0])

        messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        Assertions.assertTrue { messages[0].lastSent.isAfter(afterCreation) }
        Assertions.assertEquals(1, messages[0].resentCount)
    }

    @Test
    fun `Verify registerAckForMessage`() {
        val requestId = Uuid.random()
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val messageId = messageHeader.messageData.messageId
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        messagePendingAckRepository.storeMessagePendingAck(requestId, messageHeader, payload.toByteArray(), emailList)

        var messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        Assertions.assertEquals(false, messages[0].ackReceived)

        // Mark that we have received an Ack for the message.
        // This should set the ack_received flag to true, so it is not returned by find() anymore
        messagePendingAckRepository.registerAckForMessage(messageId)

        messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        Assertions.assertEquals(0, messages.size)
    }

    @Test
    fun `Perform entire process`() {
        val requestId = Uuid.random()
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val messageId = messageHeader.messageData.messageId
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        messagePendingAckRepository.storeMessagePendingAck(requestId, messageHeader, payload.toByteArray(), emailList)

        // max resends is set to 2 in the test setup
        // The first 2 times we search, we find the pending message, and marks that we have resent it. The 3rd time we do not find it.
        var messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        messagePendingAckRepository.markResent(messages[0])
        messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        messagePendingAckRepository.markResent(messages[0])
        messages = messagePendingAckRepository.findMessagesToResend(Instant.now())
        Assertions.assertEquals(0, messages.size)
    }

    private fun readMessageHeaderFromTestFile(fileName: String): MessageHeader {
        val testMessage = String(
            this::class.java.classLoader
                .getResourceAsStream(fileName)!!.readAllBytes()
        )
        return xmlMarshaller.unmarshal(testMessage, Envelope::class.java).header.messageHeader()
    }
}
