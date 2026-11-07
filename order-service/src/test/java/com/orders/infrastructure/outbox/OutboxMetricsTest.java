package com.orders.infrastructure.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxMetrics metrics = new OutboxMetrics(outboxRepository, registry);

    @Test
    void reportsTheFailedRecordCountFromTheLastRefresh() {
        when(outboxRepository.countByStatus("FAILED")).thenReturn(3L, 1L);

        assertEquals(0, failedGauge());
        metrics.refresh();
        assertEquals(3, failedGauge());
        metrics.refresh();
        assertEquals(1, failedGauge());
    }

    private double failedGauge() {
        return registry.get("outbox.records.failed").gauge().value();
    }
}
