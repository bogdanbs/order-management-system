CREATE TABLE event_store.order_ownership (
    order_id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL
);

CREATE INDEX idx_order_ownership_subject
    ON event_store.order_ownership (owner_subject, order_id);

ALTER TABLE query_side.order_summary
    ADD COLUMN owner_subject VARCHAR(255);

CREATE INDEX idx_order_summary_owner_created
    ON query_side.order_summary (owner_subject, created_at DESC);
