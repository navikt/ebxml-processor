package no.nav.asyncreceivers.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaProducer {

    private static KafkaTemplate<String, String> kafkaTemplate;

    public static KafkaTemplate<String, String> getKafkaTemplate() {
        return kafkaTemplate;
    }

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendDeenvelopedMessage(String msg) {
        sendMessage(no.nav.asyncreceivers.kafka.KafkaTopics.TOPIC_EBXML_PAYLOAD_OUTGOING, msg);
    }

    public void sendMessage(String topicName, String msg) {
        log.info("Sending message to kafka topic " + topicName, " message: " + msg);
        kafkaTemplate.send(topicName, msg);
    }
}
