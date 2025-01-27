CREATE TABLE events
(
    event_id            UUID 	            DEFAULT gen_random_uuid()	PRIMARY KEY,
    reference_id        UUID        	    NOT NULL                    REFERENCES ebms_message_details (reference_id),
    content_id          VARCHAR(256)        NULL,
    message_id          VARCHAR(256)        NOT NULL,
    juridisk_logg_id    VARCHAR(256)        NULL,
    event_message       VARCHAR(256)        NOT NULL,
    created_at          TIMESTAMP           DEFAULT now()
);
