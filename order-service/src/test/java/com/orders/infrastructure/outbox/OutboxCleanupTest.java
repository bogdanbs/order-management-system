package com.orders.infrastructure.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OutboxCleanupTest {
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);

    @Test
    void pollingModeDeletesOnlyPublishedRecords() {
        new OutboxCleanup(outboxRepository, "polling", 24).deleteExpiredRecords();

        verify(outboxRepository).deletePublishedBefore(argThat(cutoff -> isAboutHoursAgo(cutoff, 24)));
        verify(outboxRepository, never()).deleteCreatedBefore(any());
    }

    @Test
    void cdcModeDeletesRecordsRegardlessOfStatus() {
        new OutboxCleanup(outboxRepository, "cdc", 48).deleteExpiredRecords();

        verify(outboxRepository).deleteCreatedBefore(argThat(cutoff -> isAboutHoursAgo(cutoff, 48)));
        verify(outboxRepository, never()).deletePublishedBefore(any());
    }

    private static boolean isAboutHoursAgo(Instant cutoff, long hours) {
        Duration age = Duration.between(cutoff, Instant.now());
        return age.compareTo(Duration.ofHours(hours)) >= 0
                && age.compareTo(Duration.ofHours(hours).plusMinutes(1)) < 0;
    }
}
