package com.orders.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes outbox rows that are no longer needed once the retention period has passed.
 * <ul>
 *   <li>Polling mode: only {@code PUBLISHED} rows, by publication time. {@code PENDING} rows are still to be sent and
 *   {@code FAILED} rows are kept for inspection.</li>
 *   <li>CDC mode: every row, by creation time. Nothing marks rows as published there; Debezium captures the inserts
 *   from the WAL, not from the table, and its EventRouter ignores deletes.</li>
 * </ul>
 */
@Component
public class OutboxCleanup {
    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final OutboxRepository outboxRepository;
    private final boolean cdcMode;
    private final Duration retention;

    public OutboxCleanup(OutboxRepository outboxRepository,
                         @Value("${app.outbox.mode:polling}") String mode,
                         @Value("${app.outbox.retention-hours:24}") long retentionHours) {
        this.outboxRepository = outboxRepository;
        this.cdcMode = "cdc".equals(mode);
        this.retention = Duration.ofHours(retentionHours);
    }

    @Scheduled(fixedDelayString = "${app.outbox.cleanup-interval-ms:3600000}")
    public void deleteExpiredRecords() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = cdcMode
                ? outboxRepository.deleteCreatedBefore(cutoff)
                : outboxRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} outbox records older than {}", deleted, retention);
        }
    }
}
