package no.nav.emottak.ebms.persistence

import no.nav.emottak.ebms.ebmsPostgres
import no.nav.emottak.ebms.test.EndToEndTest.Companion.ebmsProviderDbContainer
import no.nav.emottak.ebms.testConfiguration
import no.nav.emottak.message.model.Addressing
import no.nav.emottak.message.model.Party
import no.nav.emottak.message.model.PartyId
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class EbmsMessageRepositoryTest {
    companion object {
        lateinit var ebmsMessageRepository: EbmsMessageRepository
        lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            ebmsMessageRepository = EbmsMessageRepository(ebmsProviderDb)
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
                statement.execute("TRUNCATE TABLE ebms_message")
            }
        }
    }

    @Test
    fun `Message details save to database`() {
        val originalMessage = buildTestPayloadMessage("Inntektsforesporsel")

        val savedMessageId = ebmsMessageRepository.saveEbmsMessageDetails(originalMessage)

        val retrievedMessage = ebmsMessageRepository.getByMessageIdAndCpaId(
            originalMessage.messageId,
            originalMessage.cpaId
        )

        Assertions.assertEquals(originalMessage.messageId, savedMessageId)

        Assertions.assertNotNull(retrievedMessage)
        Assertions.assertEquals(originalMessage.messageId, retrievedMessage?.messageId)
        Assertions.assertEquals(originalMessage.cpaId, retrievedMessage?.cpaId)
        Assertions.assertEquals(originalMessage.conversationId, retrievedMessage?.conversationId)
        Assertions.assertEquals(originalMessage.refToMessageId, retrievedMessage?.refToMessageId)
        Assertions.assertEquals(originalMessage.addressing.from.partyId[0].value, retrievedMessage?.fromPartyId)
        Assertions.assertEquals(originalMessage.addressing.from.role, retrievedMessage?.fromRole)
        Assertions.assertEquals(originalMessage.addressing.to.partyId[0].value, retrievedMessage?.toPartyId)
        Assertions.assertEquals(originalMessage.addressing.to.role, retrievedMessage?.toRole)
        Assertions.assertEquals(originalMessage.addressing.service, retrievedMessage?.service)
        Assertions.assertEquals(originalMessage.addressing.action, retrievedMessage?.action)
    }

    @Test
    fun `Message details don't save if service isn't supported`() {
        val originalMessage = buildTestPayloadMessage("HarBorgerFrikort")

        val savedMessageId = ebmsMessageRepository.saveEbmsMessageDetails(originalMessage)

        val retrievedMessage = ebmsMessageRepository.getByMessageIdAndCpaId(
            originalMessage.messageId,
            originalMessage.cpaId
        )

        Assertions.assertEquals("", savedMessageId)
        Assertions.assertNull(retrievedMessage)
    }

    private fun buildTestPayloadMessage(service: String) = PayloadMessage(
        "requestId1",
        "messageId1",
        "conversationId1",
        "cpaId1",
        Addressing(
            Party(
                listOf(PartyId("HER", "123456")),
                "Fordringshaver"
            ),
            Party(
                listOf(PartyId("HER", "456789")),
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
        "refToMessageId1"
    )
}
