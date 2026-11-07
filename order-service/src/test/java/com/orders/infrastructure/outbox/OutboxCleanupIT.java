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
// The job is created directly with the mode under test.
@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxCleanupIT {
    private static final Duration EXPIRED = Duration.ofHours(25);
    private static final Duration RECENT = Duration.ofHours(23);

    @Autowired
    private OutboxRepository outboxRepository;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteCreatedRecords() {
        outboxRepository.deleteAllById(created);
    }

    @Test
    void pollingModeDeletesOnlyRecordsPublishedBeforeTheRetention() {
        UUID expiredPublished = record("PUBLISHED", EXPIRED, EXPIRED);
        UUID recentlyPublished = record("PUBLISHED", EXPIRED, RECENT);
        UUID oldPending = record("PENDING", EXPIRED, null);
        UUID oldFailed = record("FAILED", EXPIRED, null);

        new OutboxCleanup(outboxRepository, "polling", 24).deleteExpiredRecords();

        assertThat(outboxRepository.existsById(expiredPublished)).isFalse();
        assertThat(outboxRepository.findAllById(List.of(recentlyPublished, oldPending, oldFailed))).hasSize(3);
    }

    @Test
    void cdcModeDeletesEveryRecordCreatedBeforeTheRetention() {
        UUID old = record("PENDING", EXPIRED, null);
        UUID recent = record("PENDING", RECENT, null);

        new OutboxCleanup(outboxRepository, "cdc", 24).deleteExpiredRecords();

        assertThat(outboxRepository.existsById(old)).isFalse();
        assertThat(outboxRepository.existsById(recent)).isTrue();
    }

    private UUID record(String status, Duration createdAgo, Duration processedAgo) {
        Instant now = Instant.now();
        OutboxRecord record = new OutboxRecord();
        record.setAggregateId(UUID.randomUUID());
        record.setAggregateType("Order");
        record.setEventType("OrderCreated");
        record.setPayload("{}");
        record.setTopic(KafkaTopics.ORDER_EVENTS);
        record.setStatus(status);
        record.setCreatedAt(now.minus(createdAgo));
        record.setProcessedAt(processedAgo == null ? null : now.minus(processedAgo));
        outboxRepository.save(record);
        created.add(record.getId());
        return record.getId();
    }
}
