package com.orders.infrastructure.outbox;

import com.orders.infrastructure.messaging.KafkaTopics;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final OutboxPublisher publisher = new OutboxPublisher(outboxRepository, kafkaTemplate);

    @Test
    void holdsBackLaterEventsOfAnAggregateWhoseEarlierEventFailedToSend() {
        UUID failingAggregate = UUID.randomUUID();
        UUID otherAggregate = UUID.randomUUID();
        OutboxRecord first = record(failingAggregate, "first");
        OutboxRecord second = record(failingAggregate, "second");
        OutboxRecord other = record(otherAggregate, "other");
        when(outboxRepository.lockNextBatch()).thenReturn(List.of(first, second, other));
        when(kafkaTemplate.send(anyString(), eq(failingAggregate.toString()), eq("first")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        when(kafkaTemplate.send(anyString(), eq(otherAggregate.toString()), eq("other")))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), eq("second"));
        assertEquals(1, first.getRetryCount());
        assertEquals("PENDING", second.getStatus());
        assertEquals(0, second.getRetryCount());
        assertEquals("PUBLISHED", other.getStatus());
    }

    private OutboxRecord record(UUID aggregateId, String payload) {
        OutboxRecord record = new OutboxRecord();
        record.setAggregateId(aggregateId);
        record.setAggregateType("Order");
        record.setEventType("OrderItemAdded");
        record.setPayload(payload);
        record.setTopic(KafkaTopics.ORDER_EVENTS);
        return record;
    }
}
