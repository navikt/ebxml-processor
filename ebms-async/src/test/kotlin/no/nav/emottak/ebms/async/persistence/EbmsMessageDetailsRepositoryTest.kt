package no.nav.emottak.ebms.async.persistence

import no.nav.emottak.ebms.async.ebmsPostgres
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.testConfiguration
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.toEbmsMessageDetails
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.time.Instant

class EbmsMessageDetailsRepositoryTest {
    companion object {
        lateinit var ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
        lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            ebmsMessageDetailsRepository = EbmsMessageDetailsRepository(ebmsProviderDb)
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
                statement.execute("DELETE FROM ebms_message_details")
            }
        }
    }

    @Test
    fun `Message details get saved to database`() {
        val originalMessage = buildTestPayloadMessage("Inntektsforesporsel")
        val originalMessageDetails = originalMessage.toEbmsMessageDetails()

        val savedMessageRequestId = ebmsMessageDetailsRepository.saveEbmsMessageDetails(originalMessageDetails)

        val retrievedMessage = ebmsMessageDetailsRepository.getByRequestId(originalMessageDetails.requestId)

        Assertions.assertNotNull(savedMessageRequestId)

        Assertions.assertNotNull(retrievedMessage)
        Assertions.assertEquals(originalMessage.messageId, retrievedMessage?.messageId)
        Assertions.assertEquals(originalMessage.requestId, retrievedMessage?.requestId.toString())
        Assertions.assertEquals(originalMessage.cpaId, retrievedMessage?.cpaId)
        Assertions.assertEquals(originalMessage.conversationId, retrievedMessage?.conversationId)
        Assertions.assertEquals(originalMessage.refToMessageId, retrievedMessage?.refToMessageId)
        Assertions.assertEquals(originalMessage.addressing.from.role, retrievedMessage?.fromRole)
        Assertions.assertEquals(originalMessage.addressing.to.role, retrievedMessage?.toRole)
        Assertions.assertEquals(originalMessage.addressing.service, retrievedMessage?.service)
        Assertions.assertEquals(originalMessage.addressing.action, retrievedMessage?.action)
        Assertions.assertEquals(originalMessage.sentAt?.toEpochMilli(), retrievedMessage?.sentAt?.toEpochMilli())

        // PartyID serializing check
        Assertions.assertEquals("orgnummer:456789", retrievedMessage?.fromPartyId)
        Assertions.assertEquals("HER:123456", retrievedMessage?.toPartyId)
    }

    @Test
    fun `Message details don't get saved if service isn't supported`() {
        val originalMessage = buildTestPayloadMessage("HarBorgerFrikort")
        val originalMessageDetails = originalMessage.toEbmsMessageDetails()

        val savedMessageRequestId = ebmsMessageDetailsRepository.saveEbmsMessageDetails(originalMessageDetails)

        val retrievedMessage = ebmsMessageDetailsRepository.getByRequestId(originalMessageDetails.requestId)

        Assertions.assertNull(savedMessageRequestId)
        Assertions.assertNull(retrievedMessage)
    }

    private fun buildTestPayloadMessage(service: String) = PayloadMessage(
        "e491180e-eea6-41d6-ac5b-d232c9fb115f",
        "messageId1",
        "conversationId1",
        "cpaId1",
        Addressing(
            Party(
                listOf(PartyId("HER", "123456")),
                "Fordringshaver"
            ),
            Party(
                listOf(PartyId("orgnummer", "456789")),
                "Ytelsesutbetaler"
            ),
            service,
            "Foresporsel"
        ),
        Payload(
            "Payload 1".toByteArray(),
            "application/json",
            "contentId1"
        ),
        null,
        "refToMessageId1",
        Instant.now(),
        duplicateElimination = false
    )
}
