package com.orders.infrastructure.ownership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderOwnershipRepository extends JpaRepository<OrderOwnershipRecord, UUID> {
    Optional<OrderOwnershipRecord> findByOrderIdAndOwnerSubject(UUID orderId, String ownerSubject);
}
