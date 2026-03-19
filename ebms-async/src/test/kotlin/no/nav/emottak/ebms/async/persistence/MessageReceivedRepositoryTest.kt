package no.nav.emottak.ebms.async.persistence

import no.nav.emottak.ebms.async.ebmsPostgres
import no.nav.emottak.ebms.async.persistence.repository.MessageReceivedRepository
import no.nav.emottak.ebms.async.processing.createPayloadMessage
import no.nav.emottak.ebms.async.testConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.uuid.Uuid

class MessageReceivedRepositoryTest {
    companion object {
        lateinit var messageReceivedRepository: MessageReceivedRepository
        lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            messageReceivedRepository = MessageReceivedRepository(ebmsProviderDb)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            ebmsProviderDbContainer.stop()
        }
    }

    @Test
    fun `Message gets saved to database`() {
        val originalMessage = createPayloadMessage()
        val originalReferenceId = Uuid.parse(originalMessage.requestId)

        messageReceivedRepository.messageReceived(originalMessage)

        val messageReceived = messageReceivedRepository.getByReferenceId(originalReferenceId)

        Assertions.assertNotNull(messageReceived)
        Assertions.assertEquals(originalReferenceId, messageReceived!!.referenceId)
    }

    @Test
    fun `Saved message gets acknowledged`() {
        val originalMessage = createPayloadMessage()

        messageReceivedRepository.messageReceived(originalMessage)
        val unacknowledgedMessageReceived = messageReceivedRepository.getMessageReceived(originalMessage)
        Assertions.assertFalse(unacknowledgedMessageReceived!!.acknowledged)

        messageReceivedRepository.messageAcknowledged(originalMessage)
        val acknowledgedMessageReceived = messageReceivedRepository.getMessageReceived(originalMessage)
        Assertions.assertTrue(acknowledgedMessageReceived!!.acknowledged)
    }

    @Test
    fun `Unsaved (new) message gets returns null`() {
        val originalMessage = createPayloadMessage()

        val messageReceived = messageReceivedRepository.getByReferenceId(Uuid.parse(originalMessage.requestId))

        Assertions.assertNull(messageReceived)
    }
}
