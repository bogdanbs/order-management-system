package com.orders.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxRecord, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxRecord> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
