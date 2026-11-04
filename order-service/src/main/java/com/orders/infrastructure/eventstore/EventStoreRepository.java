package com.orders.infrastructure.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventStoreRepository extends JpaRepository<EventRecord, UUID> {
    List<EventRecord> findByAggregateIdOrderBySequenceNumberAsc(UUID aggregateId);

    List<EventRecord> findByAggregateIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID aggregateId, long sequenceNumber);

    List<EventRecord> findAllByOrderByOccurredAtAsc();

    long countByAggregateId(UUID aggregateId);
}
