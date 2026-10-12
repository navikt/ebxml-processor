ALTER TABLE message_pending_ack
    ADD COLUMN cpa_id VARCHAR(256),
    ADD COLUMN conversation_id VARCHAR(256);

-- Used to look up a pending message by CPA and conversation id when RefToMessageId is missing from an incoming MessageError
CREATE INDEX idx_message_pending_ack_cpa_conversation_not_acked
    ON message_pending_ack (cpa_id, conversation_id)
    WHERE ack_received = false;
