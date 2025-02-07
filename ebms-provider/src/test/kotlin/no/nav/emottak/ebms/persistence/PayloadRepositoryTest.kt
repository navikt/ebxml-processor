package no.nav.emottak.ebms.persistence

import no.nav.emottak.ebms.ebmsPostgres
import no.nav.emottak.ebms.persistence.repository.PayloadRepository
import no.nav.emottak.ebms.testConfiguration
import no.nav.emottak.message.model.AsyncPayload
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.util.*

class PayloadRepositoryTest {
    companion object {
        lateinit var payloadRepository: PayloadRepository
        lateinit var ebmsProviderDbContainer: PostgreSQLContainer<Nothing>
        lateinit var ebmsProviderDb: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            ebmsProviderDbContainer = ebmsPostgres()
            ebmsProviderDbContainer.start()
            ebmsProviderDb = Database(ebmsProviderDbContainer.testConfiguration())
            ebmsProviderDb.migrate(ebmsProviderDb.dataSource)
            payloadRepository = PayloadRepository(ebmsProviderDb)
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
                statement.execute("DELETE FROM payload")
            }
        }
    }

    @Test
    fun `Payload get saved to database`() {
        val originalReferenceId = UUID.randomUUID()
        val originalPayload = AsyncPayload(
            originalReferenceId,
            "contentId1",
            "application/pkcs7-mime",
            "Payload test content 1".toByteArray()
        )

        payloadRepository.updateOrInsert(originalPayload)

        val retrievedPayloads = payloadRepository.getByReferenceId(originalReferenceId)

        Assertions.assertNotNull(retrievedPayloads)
        Assertions.assertEquals(1, retrievedPayloads.size)
        Assertions.assertEquals(originalPayload.referenceId, retrievedPayloads[0].referenceId)
        Assertions.assertEquals(originalPayload.contentId, retrievedPayloads[0].contentId)
        Assertions.assertEquals(originalPayload.contentType, retrievedPayloads[0].contentType)
        Assertions.assertEquals(String(originalPayload.content), String(retrievedPayloads[0].content))
    }

    @Test
    fun `Multiple payloads can be found by reference ID`() {
        val originalReferenceId = UUID.randomUUID()
        val originalPayload1 = AsyncPayload(
            originalReferenceId,
            "contentId1",
            "application/pkcs7-mime",
            "Payload test content 1".toByteArray()
        )
        val originalPayload2 = AsyncPayload(
            originalReferenceId,
            "contentId2",
            "application/pkcs7-mime",
            "Payload test content 2".toByteArray()
        )
        // With different reference ID
        val originalPayload3 = AsyncPayload(
            UUID.randomUUID(),
            "contentId3",
            "application/pkcs7-mime",
            "Payload test content 3".toByteArray()
        )

        payloadRepository.updateOrInsert(originalPayload1)
        payloadRepository.updateOrInsert(originalPayload2)
        payloadRepository.updateOrInsert(originalPayload3)

        val retrievedPayloads = payloadRepository.getByReferenceId(originalReferenceId)

        Assertions.assertNotNull(retrievedPayloads)
        Assertions.assertEquals(2, retrievedPayloads.size)
        Assertions.assertEquals(originalReferenceId, retrievedPayloads[0].referenceId)
    }
}
