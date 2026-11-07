package com.orders.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.outbox.mode", havingValue = "polling", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxRecord> pending = outboxRepository.lockNextBatch();
        for (OutboxRecord record : pending) {
            try {
                kafkaTemplate.send(record.getTopic(), record.getAggregateId().toString(), record.getPayload())
                        .get(3, TimeUnit.SECONDS);
                record.setStatus("PUBLISHED");
                record.setProcessedAt(Instant.now());
            } catch (Exception e) {
                record.setRetryCount(record.getRetryCount() + 1);
                if (record.getRetryCount() >= MAX_RETRIES) {
                    record.setStatus("FAILED");
                }
                log.warn("Failed to publish outbox record {}: {}", record.getId(), e.getMessage());
            }
            outboxRepository.save(record);
        }
    }
}
