package com.orders.infrastructure.outbox;

import com.orders.TestcontainersConfiguration;
import com.orders.infrastructure.messaging.KafkaTopics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxMetricsIT {
    @Autowired
    private OutboxMetrics outboxMetrics;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private MeterRegistry registry;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteCreatedRecords() {
        outboxRepository.deleteAllById(created);
    }

    @Test
    void failedGaugeCountsOnlyFailedRecords() {
        outboxMetrics.refresh();
        double before = failedGauge();
        record("FAILED");
        record("FAILED");
        record("PENDING");
        record("PUBLISHED");

        outboxMetrics.refresh();

        assertThat(failedGauge()).isEqualTo(before + 2);
    }

    private double failedGauge() {
        return registry.get("outbox.records.failed").gauge().value();
    }

    private void record(String status) {
        OutboxRecord record = new OutboxRecord();
        record.setAggregateId(UUID.randomUUID());
        record.setAggregateType("Order");
        record.setEventType("OrderCreated");
        record.setPayload("{}");
        record.setTopic(KafkaTopics.ORDER_EVENTS);
        record.setStatus(status);
        outboxRepository.save(record);
        created.add(record.getId());
    }
}
