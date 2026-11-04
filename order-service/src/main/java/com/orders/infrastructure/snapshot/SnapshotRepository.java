package com.orders.infrastructure.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SnapshotRepository extends JpaRepository<SnapshotRecord, UUID> {
}
