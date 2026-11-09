package com.orders.infrastructure.ownership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_ownership", schema = "event_store")
public class OrderOwnershipRecord {
    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "owner_subject", nullable = false)
    private String ownerSubject;

    protected OrderOwnershipRecord() {
    }

    public OrderOwnershipRecord(UUID orderId, String ownerSubject) {
        this.orderId = orderId;
        this.ownerSubject = ownerSubject;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }
}
