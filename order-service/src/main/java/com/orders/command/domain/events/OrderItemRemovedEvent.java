package com.orders.command.domain.events;

import java.util.UUID;

public class OrderItemRemovedEvent extends DomainEvent {
    private UUID productId;
    private int quantity;

    @Override
    public String getEventType() {
        return "OrderItemRemoved";
    }

    @Override
    public int getEventVersion() {
        return 1;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
