package com.orders.infrastructure.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.Order;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class SnapshotService {
    @Value("${app.snapshot.threshold:50}")
    private int threshold;

    private final SnapshotRepository snapshotRepository;
    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;

    public SnapshotService(SnapshotRepository snapshotRepository,
                           EventStoreRepository eventStoreRepository,
                           ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.eventStoreRepository = eventStoreRepository;
        this.objectMapper = objectMapper;
    }

    public void saveIfThreshold(Order order, UUID aggregateId, String aggregateType) {
        long count = eventStoreRepository.countByAggregateId(aggregateId);
        if (count > 0 && count % threshold == 0) {
            SnapshotRecord snap = new SnapshotRecord();
            snap.setAggregateId(aggregateId);
            snap.setAggregateType(aggregateType);
            snap.setSequenceNumber(order.getVersion());
            snap.setPayload(serialize(order));
            snapshotRepository.save(snap);
        }
    }

    public Optional<SnapshotRecord> loadSnapshot(UUID aggregateId) {
        return snapshotRepository.findById(aggregateId);
    }

    public Order deserializeOrder(SnapshotRecord snap) {
        try {
            return objectMapper.readValue(snap.getPayload(), Order.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize order snapshot", e);
        }
    }

    private String serialize(Order order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order snapshot", e);
        }
    }
}
