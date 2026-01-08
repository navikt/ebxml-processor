package no.nav.emottak.ebms.async

import no.nav.emottak.ebms.async.kafka.consumer.RETRY_AFTER
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Properties
import kotlin.uuid.Uuid

fun main() {
    val client = LocalTestClient()
    client.putMessageOnTopic()
}

class LocalTestClient {

//    val kafkaBrokerUrl = "//localhost:32777"
    val kafkaBrokerUrl = getRunningKafkaBrokerUrl()
    val kafkaProperties = Properties().apply {
        put("bootstrap.servers", kafkaBrokerUrl)
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    }

    val kafkaProducer = KafkaProducer<String, String>(kafkaProperties)

    fun putMessageOnTopic() {
        // See ebms_kafka_queues.conf and application.conf
//        val topic = "team-emottak.smtp.in.ebxml.payload"
        val topic = "team-emottak.ebxml.retry"
        val key = Uuid.random().toString()
        val value = readClasspathFile("testPayload.xml")!!
        val headers = listOf(
            RecordHeader(RETRY_AFTER, LocalDateTime.now().toString().toByteArray(StandardCharsets.UTF_8)),
            RecordHeader(TESTMESSAGE_FAIL_HEADER, "1".toByteArray(StandardCharsets.UTF_8))
        )

        sendMessage(topic, key, value, headers)
    }

    fun sendMessage(topic: String, key: String, value: String, headers: List<Header> = emptyList()) {
        val record = ProducerRecord(topic, null, key, value, headers)
        kafkaProducer.send(record) { metadata, exception ->
            if (exception == null) {
                println("Message with key ${record.key()} sent successfully to topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata.offset()}")
            } else {
                System.err.println("Error sending message: ${exception.message}")
            }
        }
        kafkaProducer.flush() // Ensure all buffered records are sent
    }

    fun readClasspathFile(fileName: String): String? {
        val inputStream: InputStream? = this::class.java.getResourceAsStream("/$fileName")
        return inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }
}
