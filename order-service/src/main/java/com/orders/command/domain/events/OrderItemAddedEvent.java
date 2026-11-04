package com.orders.command.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemAddedEvent extends DomainEvent {
    private UUID productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;

    @Override
    public String getEventType() {
        return "OrderItemAdded";
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
