package com.orders.command.domain.events;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class DomainEvent {
    private UUID eventId = UUID.randomUUID();
    private UUID aggregateId;
    private Instant occurredAt = Instant.now();
    private Map<String, String> metadata = new HashMap<>();

    public abstract String getEventType();

    public abstract int getEventVersion();

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
