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
        Assertions.assertFalse(messageReceivedRepository.isAcknowledged(originalMessage)!!)

        messageReceivedRepository.messageAcknowledged(originalMessage)
        Assertions.assertTrue(messageReceivedRepository.isAcknowledged(originalMessage)!!)
    }

    @Test
    fun `Any acknowledged delivery marks matching deliveries as acknowledged despite failed retries`() {
        repeat(3) { acknowledgedIndex ->
            val originalMessage = createPayloadMessage()
            val deliveries = List(3) { originalMessage.copy(requestId = Uuid.random().toString()) }

            deliveries.forEach { messageReceivedRepository.messageReceived(it) }
            deliveries.forEach { Assertions.assertFalse(messageReceivedRepository.isAcknowledged(it)!!) }

            messageReceivedRepository.messageAcknowledged(deliveries[acknowledgedIndex])

            deliveries.forEach { Assertions.assertTrue(messageReceivedRepository.isAcknowledged(it)!!) }

            deliveries.filterIndexed { index, _ -> index != acknowledgedIndex }.forEach {
                messageReceivedRepository.messageReceived(it)
            }

            deliveries.forEach { Assertions.assertTrue(messageReceivedRepository.isAcknowledged(it)!!) }
        }
    }

    @Test
    fun `Acknowledged deliveries must match conversation message and CPA IDs`() {
        val originalMessage = createPayloadMessage()
        messageReceivedRepository.messageReceived(originalMessage)

        listOf(
            originalMessage.copy(requestId = Uuid.random().toString(), conversationId = Uuid.random().toString()),
            originalMessage.copy(requestId = Uuid.random().toString(), messageId = Uuid.random().toString()),
            originalMessage.copy(requestId = Uuid.random().toString(), cpaId = "other-cpa")
        ).forEach { messageReceivedRepository.messageAcknowledged(it) }

        Assertions.assertFalse(messageReceivedRepository.isAcknowledged(originalMessage)!!)
    }

    @Test
    fun `Get new incoming message returns null`() {
        val originalMessage = createPayloadMessage()

        val messageReceivedByReference = messageReceivedRepository.getByReferenceId(Uuid.parse(originalMessage.requestId))
        Assertions.assertNull(messageReceivedByReference)

        Assertions.assertFalse(messageReceivedRepository.isAcknowledged(originalMessage))
    }
}
