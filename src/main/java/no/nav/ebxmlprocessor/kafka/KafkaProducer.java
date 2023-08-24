package no.nav.ebxmlprocessor.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendDeenvelopedMessage(String msg) {
        sendMessage(KafkaTopics.TOPIC_EBXML_PAYLOAD_DEENVELOPED, msg);
    }

    public void sendMessage(String topicName, String msg) {
        log.info("Sending message to kafka topic " + topicName);
        kafkaTemplate.send(topicName, msg);
    }
}
