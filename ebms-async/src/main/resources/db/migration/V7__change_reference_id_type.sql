ALTER TABLE payload
    ALTER COLUMN reference_id TYPE UUID
    USING reference_id::UUID;
