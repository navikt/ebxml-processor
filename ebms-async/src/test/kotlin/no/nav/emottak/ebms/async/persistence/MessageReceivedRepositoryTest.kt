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
import java.util.UUID

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
        val originalReferenceId = UUID.fromString(originalMessage.requestId)

        messageReceivedRepository.updateOrInsert(originalMessage)

        val messageReceivedId = messageReceivedRepository.getByReferenceId(originalReferenceId)

        Assertions.assertNotNull(messageReceivedId)
        Assertions.assertEquals(originalReferenceId, messageReceivedId)
    }

    @Test
    fun `Saved message gets flagged as duplicate`() {
        val originalMessage = createPayloadMessage()

        messageReceivedRepository.updateOrInsert(originalMessage)

        val isDuplicate = messageReceivedRepository.isDuplicateMessage(originalMessage)

        Assertions.assertEquals(true, isDuplicate)
    }

    @Test
    fun `Unsaved message gets flagged as not duplicate`() {
        val originalMessage = createPayloadMessage()

        val isDuplicate = messageReceivedRepository.isDuplicateMessage(originalMessage)

        Assertions.assertEquals(false, isDuplicate)
    }
}
