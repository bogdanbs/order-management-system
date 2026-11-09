CREATE INDEX idx_order_item_order_product
    ON query_side.order_item_view (order_id, product_id);

CREATE INDEX idx_event_store_rebuild_order
    ON event_store.event_store (aggregate_id, sequence_number, id);
