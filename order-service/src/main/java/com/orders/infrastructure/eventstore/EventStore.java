package com.orders.infrastructure.eventstore;

import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.upcasting.UpcasterChain;

import java.util.List;
import java.util.UUID;

public interface EventStore {
    void append(List<DomainEvent> events, UUID aggregateId, String aggregateType, long expectedVersion);

    List<DomainEvent> loadEvents(UUID aggregateId, UpcasterChain upcasterChain);

    List<DomainEvent> loadEventsAfter(UUID aggregateId, long afterSequenceNumber, UpcasterChain upcasterChain);
}
