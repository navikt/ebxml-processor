package no.nav.ebxmlprocessor.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaTopics {
    public static final String TOPIC_EBXML_PAYLOAD_OUTGOING = "team-emottak.ebxml-payload-outgoing";
    public static final String TOPIC_EBXML_PAYLOAD_DEENVELOPED = "team-emottak.ebxml-payload-deenveloped";

}
