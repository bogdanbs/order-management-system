package com.orders.infrastructure.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "snapshots", schema = "event_store")
public class SnapshotRecord {
    @Id
    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(name = "aggregate_type")
    private String aggregateType;

    @Column(name = "sequence_number")
    private long sequenceNumber;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
