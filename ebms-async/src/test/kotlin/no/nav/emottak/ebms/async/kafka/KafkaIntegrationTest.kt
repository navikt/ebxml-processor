package no.nav.emottak.ebms.async.kafka

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.emottak.ebms.async.configuration.config
import no.nav.emottak.ebms.async.kafka.consumer.FailedMessageKafkaHandler
import no.nav.emottak.ebms.async.kafka.consumer.getRecord
import no.nav.emottak.ebms.async.kafka.consumer.getRetryRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.testcontainers.shaded.com.google.common.io.Resources
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.exists

class KafkaIntegrationTest {
    val kafkaProps: Properties = Properties().apply {
        if (noLocalKafkaEnv()) return@apply
        load(
            Resources.getResource("kafka/kafkaenv-local.properties")
                .openStream()
        )
        setProperty("EBMS_PAYLOAD_RECEIVER", "true")
    }.also {
        it.forEach {
            System.setProperty(it.key as String, it.value as String)
        }
    }
    val kafkaConfig = config()

    fun noLocalKafkaEnv(): Boolean {
        return try {
            !Path(Resources.getResource("kafka/kafkaenv-local.properties").path).exists()
        } catch (ex: Exception) {
            true
        }
    }

    @Test
    @DisabledIf("noLocalKafkaEnv")
    fun testGetRecord() {
        if (noLocalKafkaEnv()) return
        val record = getRecord(
            kafkaConfig.kafkaPayloadReceiver.topic,
            kafkaConfig.kafka
        )
        assert(
            record?.key() != null
        )
    }

    @Test
    @DisabledIf("noLocalKafkaEnv")
    fun leggTilRetry() {
        if (noLocalKafkaEnv()) return
        val failedMessageQueue = FailedMessageKafkaHandler()
        runTest {
            val record = getRecord(
                fromOffset = 9379942,
                topic = kafkaConfig.kafkaPayloadReceiver.topic,
                requestedRecords = 2,
                kafka = kafkaConfig.kafka
            )!!
            runBlocking {
                failedMessageQueue.sendToRetry(
                    record
                )
            }
            val retryRecord = getRetryRecord()
            assert(retryRecord?.key() != null)
        }
    }
}
