-- Reserva recuperável para publicação Kafka sem manter locks durante I/O.
ALTER TABLE outbox ADD COLUMN lease_id uuid;
ALTER TABLE outbox ADD COLUMN lease_until timestamptz;
CREATE INDEX outbox_unpublished_key
    ON outbox(topic, partition_key, created_at, event_id)
    WHERE published_at IS NULL;
