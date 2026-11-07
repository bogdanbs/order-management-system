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
     * Locks the next pending batch for this poller. SKIP LOCKED lets several replicas poll in parallel; rows
     * whose aggregate has an older pending row held by another poller are left out so that each aggregate's
     * events are still published in order.
     */
    @Query(nativeQuery = true, value = """
            WITH batch AS (
                SELECT id FROM outbox.outbox_events
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT 50
                FOR UPDATE SKIP LOCKED
            )
            SELECT o.* FROM outbox.outbox_events o
            JOIN batch b ON b.id = o.id
            WHERE NOT EXISTS (
                SELECT 1 FROM outbox.outbox_events older
                WHERE older.aggregate_id = o.aggregate_id
                  AND older.status = 'PENDING'
                  AND older.created_at < o.created_at
                  AND older.id NOT IN (SELECT id FROM batch)
            )
            ORDER BY o.created_at
            """)
    List<OutboxRecord> lockNextBatch();

    @Transactional
    @Modifying
    @Query("DELETE FROM OutboxRecord r WHERE r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
