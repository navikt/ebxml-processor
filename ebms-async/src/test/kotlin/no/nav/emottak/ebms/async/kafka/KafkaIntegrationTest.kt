package no.nav.emottak.ebms.kafka

import no.nav.emottak.ebms.configuration.config
import no.nav.emottak.ebms.messaging.getRecord
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.com.google.common.io.Resources
import java.util.Properties

class KafkaIntegrationTest {

    val kafkaProps: Properties = Properties().apply {
        load(
            Resources.getResource("kafka/kafkaenv-local.properties")
                .openStream()
        )
        println("setting env")
        setProperty("EBMS_PAYLOAD_RECEIVER", "true")
    }.also {
        it.forEach {
            System.setProperty(it.key as String, it.value as String)
        }
    }
    val kafkaConfig = config()

    @Test
    fun testgetrecord() {
        val record = getRecord(
            kafkaConfig.kafkaPayloadReceiver.topic,
            kafkaConfig.kafka
        )
        assert(
            record?.key() != null
        )
    }

    @Test
    fun leggTilRetry() {
    }
}
