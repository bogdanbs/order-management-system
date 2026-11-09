ALTER TABLE outbox.outbox_events
    ADD COLUMN sequence_number BIGINT;

UPDATE outbox.outbox_events AS outbox_event
SET sequence_number = event_record.sequence_number
FROM event_store.event_store AS event_record
WHERE event_record.aggregate_id = outbox_event.aggregate_id
  AND event_record.payload::jsonb ->> 'eventId' = outbox_event.payload::jsonb ->> 'eventId';

WITH max_sequences AS (
    SELECT aggregate_id, coalesce(max(sequence_number), 0) AS max_sequence
    FROM outbox.outbox_events
    GROUP BY aggregate_id
),
unsequenced AS (
    SELECT outbox_event.id,
           sequences.max_sequence
               + row_number() OVER (PARTITION BY outbox_event.aggregate_id ORDER BY outbox_event.created_at, outbox_event.id)
               AS sequence_number
    FROM outbox.outbox_events AS outbox_event
    JOIN max_sequences AS sequences ON sequences.aggregate_id = outbox_event.aggregate_id
    WHERE outbox_event.sequence_number IS NULL
)
UPDATE outbox.outbox_events AS outbox_event
SET sequence_number = unsequenced.sequence_number
FROM unsequenced
WHERE outbox_event.id = unsequenced.id;

ALTER TABLE outbox.outbox_events
    ALTER COLUMN sequence_number SET NOT NULL;

CREATE UNIQUE INDEX uq_outbox_aggregate_sequence
    ON outbox.outbox_events (aggregate_id, sequence_number);

CREATE INDEX idx_outbox_status_created_sequence
    ON outbox.outbox_events (status, created_at, sequence_number);
