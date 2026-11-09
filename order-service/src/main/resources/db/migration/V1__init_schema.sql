CREATE SCHEMA IF NOT EXISTS event_store;
CREATE SCHEMA IF NOT EXISTS outbox;
CREATE SCHEMA IF NOT EXISTS query_side;

-- Event Store
CREATE TABLE IF NOT EXISTS event_store.event_store (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_version INT NOT NULL,
    payload TEXT NOT NULL,
    metadata TEXT,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_store_aggregate_seq UNIQUE (aggregate_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_event_store_occurred_at ON event_store.event_store (occurred_at);

-- Snapshots
CREATE TABLE IF NOT EXISTS event_store.snapshots (
    aggregate_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255),
    sequence_number BIGINT NOT NULL,
    payload TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE
);

-- Outbox Events
CREATE TABLE IF NOT EXISTS outbox.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox.outbox_events (status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_agg_status ON outbox.outbox_events (aggregate_id, status, created_at);

-- Query Side: Order Summary
CREATE TABLE IF NOT EXISTS query_side.order_summary (
    order_id UUID PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    status VARCHAR(255),
    total_amount NUMERIC(38, 2) DEFAULT 0,
    item_count INT NOT NULL DEFAULT 0,
    shipping_address VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_order_summary_customer
    ON query_side.order_summary (LOWER(first_name), LOWER(last_name), created_at DESC);

-- Query Side: Order Item View
CREATE TABLE IF NOT EXISTS query_side.order_item_view (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID,
    product_name VARCHAR(255),
    quantity INT NOT NULL,
    unit_price NUMERIC(38, 2)
);

CREATE INDEX IF NOT EXISTS idx_order_item_view_order_id ON query_side.order_item_view (order_id);

-- Query Side: Processed Events (Idempotency)
CREATE TABLE IF NOT EXISTS query_side.processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP(6) WITH TIME ZONE
);
