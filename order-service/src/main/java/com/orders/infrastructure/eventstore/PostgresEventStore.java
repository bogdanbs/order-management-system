package com.orders.infrastructure.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.concurrency.OptimisticConcurrencyException;
import com.orders.infrastructure.upcasting.UpcasterChain;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PostgresEventStore implements EventStore {
    private final EventStoreRepository repo;
    private final ObjectMapper objectMapper;

    public PostgresEventStore(EventStoreRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(List<DomainEvent> events, UUID aggregateId, String aggregateType, long expectedVersion) {
        long seq = expectedVersion;
        for (DomainEvent event : events) {
            seq++;
            EventRecord record = new EventRecord();
            record.setAggregateId(aggregateId);
            record.setAggregateType(aggregateType);
            record.setSequenceNumber(seq);
            record.setEventType(event.getEventType());
            record.setEventVersion(event.getEventVersion());
            record.setPayload(serializePayload(event));
            record.setMetadata(serializeMetadata(event.getMetadata()));
            try {
                repo.save(record);
            } catch (DataIntegrityViolationException e) {
                throw new OptimisticConcurrencyException(
                        "Concurrent write on aggregate " + aggregateId + " at seq " + seq, e);
            }
        }
    }

    @Override
    public List<DomainEvent> loadEvents(UUID aggregateId, UpcasterChain upcasterChain) {
        return repo.findByAggregateIdOrderBySequenceNumberAsc(aggregateId)
                .stream()
                .map(r -> deserialize(r, upcasterChain))
                .toList();
    }

    @Override
    public List<DomainEvent> loadEventsAfter(UUID aggregateId, long afterSeq, UpcasterChain upcasterChain) {
        return repo.findByAggregateIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(aggregateId, afterSeq)
                .stream()
                .map(r -> deserialize(r, upcasterChain))
                .toList();
    }

    private DomainEvent deserialize(EventRecord record, UpcasterChain upcasterChain) {
        return upcasterChain.deserialize(
                record.getEventType(), record.getEventVersion(), record.getPayload(), objectMapper);
    }

    private String serializePayload(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    private String serializeMetadata(Map<String, String> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event metadata", e);
        }
    }
}
