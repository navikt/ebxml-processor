package no.nav.asyncreceivers.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class KafkaConsumer {

    @KafkaListener(
            topics = no.nav.asyncreceivers.kafka.KafkaTopics.TOPIC_EBXML_PAYLOAD_OUTGOING,
            groupId = "EBXML_PROCESSOR_CONSUMER"
    )
    public void listen(String in) {
        log.info("Message received: " + in);
    }

}
