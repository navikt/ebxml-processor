package no.nav.emottak.ebms.async.persistence

import kotlinx.coroutines.runBlocking
import no.nav.emottak.ebms.async.ebmsPostgres
import no.nav.emottak.ebms.async.persistence.repository.ResponseAckRepository
import no.nav.emottak.ebms.async.testConfiguration
import no.nav.emottak.message.ebxml.messageHeader
import no.nav.emottak.message.model.AsyncPayload
import no.nav.emottak.message.model.EmailAddress
import no.nav.emottak.message.xml.XmlMarshaller
import no.nav.emottak.message.xml.createDocument
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.oasis_open.committees.ebxml_cppa.schema.cpp_cpa_2_0.EndpointTypeType
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageData
import org.oasis_open.committees.ebxml_msg.schema.msg_header_2_0.MessageHeader
import org.testcontainers.containers.PostgreSQLContainer
import org.xmlsoap.schemas.soap.envelope.Envelope
import java.sql.DriverManager
import java.time.Instant
import kotlin.uuid.Uuid

class ResponseAckRepositoryTest {
    companion object {
        lateinit var responseAckRepository: ResponseAckRepository
        lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            responseAckRepository = ResponseAckRepository(ebmsProviderDb, 5, 2)
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
                statement.execute("DELETE FROM responseack")
            }
        }
    }

    private val xmlMarshaller = XmlMarshaller()

    @Test
    fun `Write ResponseAck and read back`() {
        val requestId = "theRequestId"
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val messageId = messageHeader.messageData.messageId
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        responseAckRepository.storeResponse(requestId, messageHeader, payload.toByteArray(), emailList)

        // No response to be resent before 12 hours is passed
        var responses = responseAckRepository.findResponsesToResend()
        Assertions.assertEquals(0, responses.size)

        // Read again but with specified cutoff, like it would be if the insert was more than 12 hours ago
        responses = responseAckRepository.findResponsesToResend(Instant.now())
        Assertions.assertEquals(1, responses.size)

        Assertions.assertEquals(messageId, responses[0].messageId)
        Assertions.assertEquals(false, responses[0].ackReceived)
        Assertions.assertEquals(xmlMarshaller.marshal(messageHeader), xmlMarshaller.marshal(responses[0].messageHeader))
        Assertions.assertEquals(payload, String(responses[0].messageContent))
        Assertions.assertEquals(listOf(email1,email2), responses[0].emailAddressList)
        Assertions.assertTrue { responses[0].firstSent.isBefore(Instant.now()) }
        Assertions.assertTrue { responses[0].lastSent.isBefore(Instant.now()) }
        Assertions.assertEquals(0, responses[0].resentCount)
    }

    @Test
    fun `Verify markResent`() {
        val requestId = "theRequestId"
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        responseAckRepository.storeResponse(requestId, messageHeader, payload.toByteArray(), emailList)

        val afterCreation = Instant.now()
        var responses = responseAckRepository.findResponsesToResend(Instant.now())
        Assertions.assertTrue { responses[0].lastSent.isBefore(afterCreation) }
        Assertions.assertEquals(0, responses[0].resentCount)

        // Mark that we have resent the response once.
        // This should update counter and last_sent timestamp
        responseAckRepository.markResent(responses[0])

        responses = responseAckRepository.findResponsesToResend(Instant.now())
        Assertions.assertTrue { responses[0].lastSent.isAfter(afterCreation) }
        Assertions.assertEquals(1, responses[0].resentCount)
    }

    @Test
    fun `Verify registerAckForMessage`() {
        val requestId = "theRequestId"
        val payload = "theContent"
        val messageHeader = readMessageHeaderFromTestFile("signaltest/acknowledgment.xml")
        val messageId = messageHeader.messageData.messageId
        val email1 = "ab@cd.com"
        val email2 = "xy@cd.com"
        val emailList = listOf(EmailAddress(email1, EndpointTypeType.RESPONSE), EmailAddress(email2, EndpointTypeType.RESPONSE))
        responseAckRepository.storeResponse(requestId, messageHeader, payload.toByteArray(), emailList)

        var responses = responseAckRepository.findResponsesToResend(Instant.now())
        Assertions.assertEquals(false, responses[0].ackReceived)

        // Mark that we have received an Ack for the response.
        // This should set the ack_received flag to true, so it is not returned by find() anymore
        responseAckRepository.registerAckForMessage(messageId)

        responses = responseAckRepository.findResponsesToResend(Instant.now())
        Assertions.assertEquals(0, responses.size)
    }

    private fun readMessageHeaderFromTestFile(fileName: String): MessageHeader {
        val testMessage = String(this::class.java.classLoader
                .getResourceAsStream(fileName)!!.readAllBytes())
        return xmlMarshaller.unmarshal(testMessage, Envelope::class.java).header.messageHeader()
    }
}
