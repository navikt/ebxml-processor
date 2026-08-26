CREATE OR REPLACE PROCEDURE delete_old_payloads(
    p_keep_days                 IN  int,    -- Angir hvor lenge payloads skal beholdes i databasen (slett de som er eldre).
    p_batch_size                IN  int,    -- Hvor mange payloads som skal slettes om gangen.
    o_total_deleted_payloads    OUT BIGINT  -- Returnerer antall slettede rader.
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_deleted_count int;
    v_total_deleted_payloads BIGINT := 0;
    v_delete_threshold_date TIMESTAMP := NOW() - (p_keep_days || ' day')::INTERVAL;
BEGIN
    RAISE NOTICE 'delete_old_payloads: Started';
    LOOP
        DELETE FROM payload
        WHERE EXISTS (
            SELECT 1
            FROM (
                 SELECT reference_id, content_id
                 FROM payload
                 WHERE created_at < v_delete_threshold_date
                 ORDER BY created_at
                 LIMIT p_batch_size
             ) AS old_payloads
            WHERE old_payloads.reference_id = payload.reference_id
            AND old_payloads.content_id = payload.content_id
        );

        GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
        -- Avslutt loop hvis ingen flere rader igjen:
        IF v_deleted_count = 0 THEN
            RAISE NOTICE 'delete_old_payloads: No more entries left to delete - exiting the loop';
            EXIT;
        END IF;
        RAISE NOTICE 'delete_old_payloads: Deleted % rows from the payload table.', v_deleted_count;
        v_total_deleted_payloads := v_total_deleted_payloads + v_deleted_count;

        COMMIT;
        RAISE NOTICE 'delete_old_payloads: PERFORM pg_sleep(0.01)';
        PERFORM pg_sleep(0.01); -- Small pause to reduce lock contention

    END LOOP;

    RAISE NOTICE 'delete_old_payloads: COMPLETE - deleted % payloads', v_total_deleted_payloads;
    o_total_deleted_payloads := v_total_deleted_payloads;
END;
$$;