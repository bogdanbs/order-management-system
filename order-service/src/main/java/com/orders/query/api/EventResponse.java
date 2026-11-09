package com.orders.query.api;

import com.orders.infrastructure.eventstore.EventRecord;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID aggregateId,
        String aggregateType,
        Long sequenceNumber,
        String eventType,
        Integer eventVersion,
        String payload,
        String metadata,
        Instant occurredAt
) {
    public static EventResponse fromRecord(EventRecord record) {
        return new EventResponse(
                record.getId(),
                record.getAggregateId(),
                record.getAggregateType(),
                record.getSequenceNumber(),
                record.getEventType(),
                record.getEventVersion(),
                record.getPayload(),
                record.getMetadata(),
                record.getOccurredAt()
        );
    }
}
