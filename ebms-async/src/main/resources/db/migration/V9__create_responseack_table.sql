CREATE TABLE message_pending_ack
(
    message_id          UUID                NOT NULL PRIMARY KEY,
    request_id          UUID                NOT NULL,
    ack_received        BOOLEAN             NOT NULL,
    header              TEXT                NOT NULL,
    content             BYTEA		        NOT NULL,
    email_list          VARCHAR(256)        NOT NULL,
    first_sent_at       TIMESTAMP           NOT NULL,
    last_sent_at        TIMESTAMP           NOT NULL,
    resent_count        INTEGER             NOT NULL
);
