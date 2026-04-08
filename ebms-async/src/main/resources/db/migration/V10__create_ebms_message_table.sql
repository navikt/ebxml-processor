CREATE TABLE message_received
(
    request_id          UUID                NOT NULL PRIMARY KEY,
    conversation_id     VARCHAR(256)        NOT NULL,
    message_id          VARCHAR(256)        NOT NULL,
    ref_to_message_id   VARCHAR(256)        NULL,
    cpa_id              VARCHAR(256)        NOT NULL,
    sender_role         VARCHAR(256)        NOT NULL,
    sender_id           VARCHAR(256)        NOT NULL,
    receiver_role       VARCHAR(256)        NOT NULL,
    receiver_id         VARCHAR(256)        NOT NULL,
    service             VARCHAR(256)        NOT NULL,
    action              VARCHAR(256)        NOT NULL,
    received_at         TIMESTAMP           NOT NULL DEFAULT now(),
    acknowledged        BOOLEAN             NOT NULL
);

CREATE INDEX idx_message_received_duplicate_check
    ON message_received (conversation_id, message_id, cpa_id);
