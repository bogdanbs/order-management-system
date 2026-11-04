package com.orders.infrastructure.eventstore;

public record EventPayload(String eventType, int eventVersion, String aggregateId, Object data) {
}
