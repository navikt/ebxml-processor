CREATE TABLE message_received
(
    request_id          UUID                NOT NULL PRIMARY KEY,
    conversation_id     VARCHAR(256)        NOT NULL,
    message_id          VARCHAR(256)        NOT NULL,
    cpa_id              VARCHAR(256)        NOT NULL,
    received_at         TIMESTAMP           NOT NULL DEFAULT now(),
    ack_sent            BOOLEAN             NOT NULL
);