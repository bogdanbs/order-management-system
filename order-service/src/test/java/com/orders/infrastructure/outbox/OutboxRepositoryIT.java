package com.orders.infrastructure.outbox;

import com.orders.TestcontainersConfiguration;
import com.orders.infrastructure.messaging.KafkaTopics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxRepositoryIT {
    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteCreatedRecords() {
        outboxRepository.deleteAllById(created);
    }

    @Test
    void secondPollerSkipsRowsHeldByAnotherPollerAndKeepsPerAggregateOrder() {
        UUID aggregateX = UUID.randomUUID();
        UUID aggregateY = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID x1 = pending(aggregateX, 1, now.minusSeconds(3));
        UUID x2 = pending(aggregateX, 2, now.minusSeconds(2));
        UUID y1 = pending(aggregateY, 1, now.minusSeconds(1));

        List<UUID> secondBatch = new TransactionTemplate(transactionManager).execute(otherPoller -> {
            jdbc.queryForObject("SELECT id FROM outbox.outbox_events WHERE id = ? FOR UPDATE", UUID.class, x1);
            return lockNextBatchInNewTransaction();
        });

        // x1 is held by the other poller; x2 must wait behind it, y1 is free to publish.
        assertThat(secondBatch).contains(y1).doesNotContain(x1, x2);
    }

    @Test
    void returnsAllPendingRowsOfAnAggregateInOrderWhenNothingIsLocked() {
        UUID aggregate = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID first = pending(aggregate, 1, now.minusSeconds(2));
        UUID second = pending(aggregate, 2, now.minusSeconds(1));

        assertThat(lockNextBatchInNewTransaction()).containsSubsequence(first, second);
    }

    private List<UUID> lockNextBatchInNewTransaction() {
        TransactionTemplate poller = new TransactionTemplate(transactionManager);
        poller.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return poller.execute(status -> {
            // Fail instead of hanging if the query waits for the other poller's lock.
            jdbc.execute("SET LOCAL lock_timeout = '2s'");
            return outboxRepository.lockNextBatch().stream().map(OutboxRecord::getId).toList();
        });
    }

    private UUID pending(UUID aggregateId, long sequenceNumber, Instant createdAt) {
        OutboxRecord record = new OutboxRecord();
        record.setAggregateId(aggregateId);
        record.setSequenceNumber(sequenceNumber);
        record.setAggregateType("Order");
        record.setEventType("OrderCreated");
        record.setPayload("{}");
        record.setTopic(KafkaTopics.ORDER_EVENTS);
        record.setCreatedAt(createdAt);
        outboxRepository.save(record);
        created.add(record.getId());
        return record.getId();
    }
}
