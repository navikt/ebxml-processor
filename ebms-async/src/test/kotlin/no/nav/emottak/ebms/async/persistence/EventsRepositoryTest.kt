package no.nav.emottak.ebms.async.persistence

import no.nav.emottak.ebms.async.ebmsPostgres
import no.nav.emottak.ebms.async.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.ebms.async.persistence.repository.EventsRepository
import no.nav.emottak.ebms.async.testConfiguration
import no.nav.emottak.utils.common.model.Addressing
import no.nav.emottak.message.model.Event
import no.nav.emottak.utils.common.model.Party
import no.nav.emottak.utils.common.model.PartyId
import no.nav.emottak.message.model.Payload
import no.nav.emottak.message.model.PayloadMessage
import no.nav.emottak.message.model.toEbmsMessageDetails
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import kotlin.uuid.Uuid

class EventsRepositoryTest {
    companion object {
        lateinit var eventRepository: EventsRepository
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
            eventRepository = EventsRepository(ebmsProviderDb)
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
                statement.execute("DELETE FROM events")
                statement.execute("DELETE FROM ebms_message_details")
            }
        }
    }

    @Test
    fun `Event get saved to database`() {
        val originalMessage = buildTestPayloadMessage("Inntektsforesporsel")
        val originalMessageDetails = originalMessage.toEbmsMessageDetails()
        val originalEvent = buildTestEvent(originalMessageDetails.requestId)

        ebmsMessageDetailsRepository.saveEbmsMessageDetails(originalMessageDetails)
        val savedEventId = eventRepository.updateOrInsert(originalEvent)

        val retrievedEvent = eventRepository.getByEventId(savedEventId)

        Assertions.assertEquals(originalEvent.requestId, retrievedEvent?.requestId)
        Assertions.assertEquals(originalEvent.contentId, retrievedEvent?.contentId)
        Assertions.assertEquals(originalEvent.messageId, retrievedEvent?.messageId)
        Assertions.assertEquals(originalEvent.juridiskLoggId, retrievedEvent?.juridiskLoggId)
        Assertions.assertEquals(originalEvent.eventMessage, retrievedEvent?.eventMessage)
        Assertions.assertNotNull(retrievedEvent?.createdAt)
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
        duplicateElimination = false
    )

    private fun buildTestEvent(requestId: Uuid) = Event(
        Uuid.random(),
        requestId,
        "contentId1",
        "messageId1",
        "juridiskLoggId1",
        "Message has been saved to juridisk logg",
        null
    )
}
