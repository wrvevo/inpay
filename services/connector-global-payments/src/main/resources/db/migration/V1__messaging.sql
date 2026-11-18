CREATE TABLE outbox (
 event_id uuid PRIMARY KEY, topic text NOT NULL, partition_key text NOT NULL,
 payload text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz
);
CREATE INDEX outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;
CREATE TABLE inbox (consumer text NOT NULL, event_id uuid NOT NULL,
 received_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(consumer,event_id));
