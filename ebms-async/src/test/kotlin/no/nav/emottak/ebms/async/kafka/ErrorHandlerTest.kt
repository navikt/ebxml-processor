package no.nav.emottak.ebms.async.kafka

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.emottak.ebms.async.configuration.ErrorRetryPolicy
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.RETRY_COUNT_HEADER
import no.nav.emottak.ebms.async.kafka.consumer.asReceiverRecord
import no.nav.emottak.ebms.async.kafka.consumer.getRecord
import no.nav.emottak.ebms.async.processing.MessageFilterService
import no.nav.emottak.utils.config.Kafka
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ErrorHandlerTest {

    @Test
    fun testConsumeRetryQueue() {
        runTest {
            KafkaTestContainer.start()
            System.setProperty("KAFKA_BROKERS", KafkaTestContainer.bootstrapServers)
            KafkaTestContainer.createTopic(config().kafkaErrorQueue.topic)
            KafkaTestContainer.createTopic(config().kafkaPayloadProducer.topic)

            val testcontainerKafkaConfig =
                config().kafka.copy(
                    bootstrapServers = KafkaTestContainer.kafkaContainer.bootstrapServers
                )

            // This test seems to have problems with the startup,
            // so the consumer offset is initialised (after the  sendToRetry() ?) with 1 instead of 0.
            // Need to override this by explicitly setting to earliest offset
            System.setProperty("RETRY_INIT_OFFSET", "earliest")
            // Set retry after 0 minutes, to force immediate retry
            val errorHandler = FailedMessageKafkaHandler(
                kafka = testcontainerKafkaConfig,
                errorRetryPolicy = ErrorRetryPolicy(1.seconds, 10, listOf(0.minutes), listOf(2))
            )
            val processedMessages = ArrayList<ReceiverRecord<String, ByteArray>>()
            val messageFilterService = DummyMessageFilterService(errorHandler, processedMessages)

            errorHandler.sendToRetry(newRecord("test-message"))
            val record1 = getRecordFromErrorQueueAtOffset(testcontainerKafkaConfig, 0)
            assertTrue(record1?.key() == "test-message", "Melding sendt til feilhåndtering ligger på feilkø med offset 0")

            errorHandler.consumeRetryQueue(messageFilterService)
            assertTrue(processedMessages.size == 1, "Etter prosessering av feilkø er meldingen prosessert av MessageFilterService")

            errorHandler.sendToRetry(newRecord("failingAtFirstRetry"))
            val record2 = getRecordFromErrorQueueAtOffset(testcontainerKafkaConfig, 1)
            assertTrue(record2?.key() == "failingAtFirstRetry", "Melding som vil feile ligger på feilkø med offset 1")

            errorHandler.consumeRetryQueue(messageFilterService)
            assertTrue(processedMessages.size == 1, "Etter prosessering av feilkø 1 gang er meldingen IKKE prosessert av MessageFilterService")
            val record3 = getRecordFromErrorQueueAtOffset(testcontainerKafkaConfig, 2)
            assertTrue(getRetryCountHeaderValue(record3) == 1, "Etter prosessering av feilkø 1 gang ligger meldingen igjen på feilkø med offset 2, og retrycount=1")

            errorHandler.consumeRetryQueue(messageFilterService)
            assertTrue(processedMessages.size == 2, "Etter prosessering av feilkø 2 ganger er meldingen prosessert av MessageFilterService")
        }
    }

    private fun getRetryCountHeaderValue(record: ReceiverRecord<String, ByteArray>?): Int {
        val header = record?.headers()?.lastHeader(RETRY_COUNT_HEADER)?.value() ?: "0".toByteArray()
        return Integer.parseInt(String(header))
    }

    private fun newRecord(key: String): ReceiverRecord<String, ByteArray> =
        ConsumerRecord(config().kafkaErrorQueue.topic, 0, 0, key, "".toByteArray())
            .asReceiverRecord()

    private fun getRecordFromErrorQueueAtOffset(testcontainerKafkaConfig: Kafka, offset: Long): ReceiverRecord<String, ByteArray>? =
        getRecord(config().kafkaErrorQueue.topic, testcontainerKafkaConfig, offset, 1)

    @AfterEach
    fun teardown() {
        KafkaTestContainer.stop()
    }

    class DummyMessageFilterService(
        val kafkaErrorHandler: FailedMessageKafkaHandler,
        val processedMessages: MutableList<ReceiverRecord<String, ByteArray>>
    ) : MessageFilterService(
        mockk(),
        mockk(),
        mockk(),
        mockk()
    ) {
        override suspend fun filterMessage(record: ReceiverRecord<String, ByteArray>) {
            // Fail and send to retry if key starts with "fail", until retried 2 times
            val retries = 2
            if (record.key().startsWith("fail", true)) {
                val retried = record.headers().lastHeader(RETRY_COUNT_HEADER)
                val r = retried.value().decodeToString().toIntOrNull()
                if (r != null && r < retries) {
                    println("--Failing message {$r+1} time with requestId: ${record.key()} and offset ${record.offset()}")
                    kafkaErrorHandler.sendToRetry(
                        record = record,
                        reason = "Test message set to fail again"
                    )
                    return
                }
            }
            processedMessages.add(record)
            println("--Record processed OK")
        }
    }
}
