package no.nav.emottak.ebms.async.kafka

import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.RETRY_COUNT_HEADER
import no.nav.emottak.ebms.async.kafka.consumer.asReceiverRecord
import no.nav.emottak.ebms.async.kafka.consumer.getRecord
import no.nav.emottak.ebms.async.processing.MessageFilterService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

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

            val errorHandler = FailedMessageKafkaHandler(
                kafka = testcontainerKafkaConfig
            )
            val messageFilterService = mockk<MessageFilterService>()
            val processedMessages = ArrayList<ReceiverRecord<String, ByteArray>>()
            coEvery {
                messageFilterService.filterMessage(any())
            } coAnswers { processedMessages.add(firstArg<ReceiverRecord<String, ByteArray>>()) }

            errorHandler
                .sendToRetry(
                    ConsumerRecord(config().kafkaErrorQueue.topic, 0, 0, "test-message", "".toByteArray())
                        .asReceiverRecord()
                )
            launch {
                errorHandler.consumeRetryQueue(messageFilterService)
            }.join()
            val writtenRecord = getRecord(config().kafkaErrorQueue.topic, testcontainerKafkaConfig, 0, 1)
            assert(writtenRecord?.key() == "test-message")
            assert(processedMessages.isNotEmpty())
            assert(String(processedMessages[0].headers().lastHeader(RETRY_COUNT_HEADER).value()) == "1")
        }
    }

    @AfterEach
    fun teardown() {
        KafkaTestContainer.stop()
    }
}
