ALTER TABLE ebms_message_details
    ADD sent_at TIMESTAMP NULL,
    ADD created_at TIMESTAMP DEFAULT now();
