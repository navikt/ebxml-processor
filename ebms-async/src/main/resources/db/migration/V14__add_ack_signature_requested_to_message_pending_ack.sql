ALTER TABLE message_pending_ack
    ADD COLUMN ack_signature_requested BOOLEAN NOT NULL DEFAULT TRUE;
