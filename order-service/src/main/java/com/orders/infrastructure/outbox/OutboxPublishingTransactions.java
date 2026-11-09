package com.orders.infrastructure.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxPublishingTransactions {
    private static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;

    public OutboxPublishingTransactions(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public List<OutboxRecord> claimNextBatch() {
        List<OutboxRecord> records = outboxRepository.lockNextBatch();
        Instant claimedAt = Instant.now();
        for (OutboxRecord record : records) {
            record.setStatus("IN_FLIGHT");
            record.setProcessedAt(claimedAt);
        }
        outboxRepository.saveAll(records);
        return records;
    }

    @Transactional
    public void markPublished(UUID recordId) {
        OutboxRecord record = requireRecord(recordId);
        record.setStatus("PUBLISHED");
        record.setProcessedAt(Instant.now());
    }

    @Transactional
    public void markFailed(UUID recordId) {
        OutboxRecord record = requireRecord(recordId);
        record.setRetryCount(record.getRetryCount() + 1);
        record.setStatus(record.getRetryCount() >= MAX_RETRIES ? "FAILED" : "PENDING");
        record.setProcessedAt(null);
    }

    @Transactional
    public void release(UUID recordId) {
        OutboxRecord record = requireRecord(recordId);
        if ("IN_FLIGHT".equals(record.getStatus())) {
            record.setStatus("PENDING");
            record.setProcessedAt(null);
        }
    }

    private OutboxRecord requireRecord(UUID recordId) {
        return outboxRepository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Outbox record not found: " + recordId));
    }
}
