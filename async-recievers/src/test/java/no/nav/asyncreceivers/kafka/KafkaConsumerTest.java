package no.nav.asyncreceivers.kafka;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.test.context.EmbeddedKafka;

@EnableKafka
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        controlledShutdown = false,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:3333",
                "port=3333"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaConsumerTest {

    @Autowired
    private KafkaProducer kafkaProducer;

    @BeforeAll
    private void setUp() {
        kafkaProducer.sendMessage(KafkaTopics.TOPIC_EBXML_PAYLOAD_OUTGOING, "Outgoing message received");
    }
    @Test
    void listen() {
    }


}