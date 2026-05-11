CREATE INDEX idx_message_pending_ack_not_acked
    ON message_pending_ack (message_id)
    WHERE ack_received = false;
