package com.orders.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * In CDC mode nothing marks outbox rows as published, so they are deleted after a retention period. This is safe
 * because Debezium captures the inserts from the WAL, not from the table, and its EventRouter ignores deletes.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.mode", havingValue = "cdc")
public class OutboxCdcCleanup {
    private static final Logger log = LoggerFactory.getLogger(OutboxCdcCleanup.class);

    private final OutboxRepository outboxRepository;
    private final Duration retention;

    public OutboxCdcCleanup(OutboxRepository outboxRepository,
                            @Value("${app.outbox.cdc-retention-hours:24}") long retentionHours) {
        this.outboxRepository = outboxRepository;
        this.retention = Duration.ofHours(retentionHours);
    }

    @Scheduled(fixedDelayString = "${app.outbox.cleanup-interval-ms:3600000}")
    public void deleteCapturedRecords() {
        int deleted = outboxRepository.deleteCreatedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} outbox records older than {}", deleted, retention);
        }
    }
}
