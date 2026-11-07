package com.orders.infrastructure.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes {@code outbox.records.failed}: outbox records that exhausted their publish retries and need attention.
 * The count is refreshed on a schedule so that metric scrapes never query the database.
 */
@Component
public class OutboxMetrics {
    private final OutboxRepository outboxRepository;
    private final AtomicLong failedRecords = new AtomicLong();

    public OutboxMetrics(OutboxRepository outboxRepository, MeterRegistry registry) {
        this.outboxRepository = outboxRepository;
        Gauge.builder("outbox.records.failed", failedRecords, AtomicLong::get)
                .description("Outbox records marked FAILED after exhausting their publish retries")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.metrics-refresh-ms:30000}")
    public void refresh() {
        failedRecords.set(outboxRepository.countByStatus("FAILED"));
    }
}
