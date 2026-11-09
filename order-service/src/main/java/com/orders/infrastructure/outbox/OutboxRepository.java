package com.orders.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxRecord, UUID> {
    /**
     * Claims the next available batch. Expired claims are recovered after a crashed poller; older pending or
     * in-flight events prevent later events of the same aggregate from being claimed out of order.
     */
    @Query(nativeQuery = true, value = """
            WITH batch AS (
                SELECT id FROM outbox.outbox_events
                WHERE status = 'PENDING'
                   OR (status = 'IN_FLIGHT' AND processed_at < now() - INTERVAL '5 minutes')
                ORDER BY created_at, id
                LIMIT 50
                FOR UPDATE SKIP LOCKED
            )
            SELECT o.* FROM outbox.outbox_events o
            JOIN batch b ON b.id = o.id
            WHERE NOT EXISTS (
                SELECT 1 FROM outbox.outbox_events older
                WHERE older.aggregate_id = o.aggregate_id
                  AND older.sequence_number < o.sequence_number
                  AND older.status IN ('PENDING', 'IN_FLIGHT')
                  AND older.id NOT IN (SELECT id FROM batch)
            )
            ORDER BY o.created_at, o.id
            """)
    List<OutboxRecord> lockNextBatch();

    long countByStatus(String status);

    @Transactional
    @Modifying
    @Query("DELETE FROM OutboxRecord r WHERE r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);

    @Transactional
    @Modifying
    @Query("DELETE FROM OutboxRecord r WHERE r.status = 'PUBLISHED' AND r.processedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
