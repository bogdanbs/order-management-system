package com.orders.infrastructure.outbox;

import com.orders.TestcontainersConfiguration;
import com.orders.infrastructure.messaging.KafkaTopics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs in the shared polling-mode context: a cdc-mode context would try to register the Debezium connector.
@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxCdcCleanupIT {
    @Autowired
    private OutboxRepository outboxRepository;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteCreatedRecords() {
        outboxRepository.deleteAllById(created);
    }

    @Test
    void deletesRecordsOlderThanTheRetentionAndKeepsRecentOnes() {
        UUID old = record(Instant.now().minus(Duration.ofHours(25)));
        UUID recent = record(Instant.now().minus(Duration.ofHours(23)));

        new OutboxCdcCleanup(outboxRepository, 24).deleteCapturedRecords();

        assertThat(outboxRepository.existsById(old)).isFalse();
        assertThat(outboxRepository.existsById(recent)).isTrue();
    }

    private UUID record(Instant createdAt) {
        OutboxRecord record = new OutboxRecord();
        record.setAggregateId(UUID.randomUUID());
        record.setAggregateType("Order");
        record.setEventType("OrderCreated");
        record.setPayload("{}");
        record.setTopic(KafkaTopics.ORDER_EVENTS);
        record.setCreatedAt(createdAt);
        outboxRepository.save(record);
        created.add(record.getId());
        return record.getId();
    }
}
