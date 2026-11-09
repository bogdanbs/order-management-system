package com.orders.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.messaging.KafkaTopics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void write(List<DomainEvent> events, UUID aggregateId, String aggregateType, long expectedVersion) {
        long sequenceNumber = expectedVersion;
        for (DomainEvent event : events) {
            OutboxRecord record = new OutboxRecord();
            record.setAggregateId(aggregateId);
            record.setSequenceNumber(++sequenceNumber);
            record.setAggregateType(aggregateType);
            record.setEventType(event.getEventType());
            record.setPayload(serialize(event));
            record.setTopic(KafkaTopics.ORDER_EVENTS);
            outboxRepository.save(record);
        }
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }
}
