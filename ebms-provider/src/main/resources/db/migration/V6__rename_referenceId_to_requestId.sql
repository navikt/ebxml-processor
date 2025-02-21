ALTER TABLE events
    DROP CONSTRAINT events_reference_id_fkey;

ALTER TABLE ebms_message_details
    RENAME COLUMN reference_id TO request_id;

ALTER TABLE events
    RENAME COLUMN reference_id TO request_id;

ALTER TABLE events
    ADD CONSTRAINT events_request_id_fkey
        FOREIGN KEY (request_id) REFERENCES ebms_message_details(request_id);