package com.orders.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.outbox.mode", havingValue = "polling", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxPublishingTransactions publishingTransactions;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxPublishingTransactions publishingTransactions,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.publishingTransactions = publishingTransactions;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:5000}")
    public void publishPending() {
        List<OutboxRecord> pending = publishingTransactions.claimNextBatch();
        // An aggregate's later events wait for the next poll once one of its events fails, to keep their order.
        Set<UUID> blockedAggregates = new HashSet<>();
        for (OutboxRecord record : pending) {
            if (blockedAggregates.contains(record.getAggregateId())) {
                publishingTransactions.release(record.getId());
                continue;
            }
            try {
                kafkaTemplate.send(record.getTopic(), record.getAggregateId().toString(), record.getPayload())
                        .get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                blockedAggregates.add(record.getAggregateId());
                publishingTransactions.markFailed(record.getId());
                log.warn("Failed to publish outbox record {}: {}", record.getId(), e.getMessage());
                continue;
            }
            publishingTransactions.markPublished(record.getId());
        }
    }
}
