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
import no.nav.emottak.ebms.async.processing.PayloadMessageProcessor
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Properties

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
                    bootstrapServers = KafkaTestContainer.kafkaContainer.bootstrapServers,
                    properties = Properties().apply {
                        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainer.kafkaContainer.bootstrapServers)
                    }
                )

            val errorHandler = FailedMessageKafkaHandler(
                kafka = testcontainerKafkaConfig
            )
            val payloadMessageProcessor = mockk<PayloadMessageProcessor>()
            val processedMessages = ArrayList<ReceiverRecord<String, ByteArray>>()
            coEvery {
                payloadMessageProcessor.process(any())
            } coAnswers { processedMessages.add(firstArg<ReceiverRecord<String, ByteArray>>()) }

            errorHandler
                .sendToRetry(
                    ConsumerRecord(config().kafkaErrorQueue.topic, 0, 0, "test-message", "".toByteArray())
                        .asReceiverRecord()
                )
            launch {
                errorHandler.consumeRetryQueue(payloadMessageProcessor)
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
