package com.orders.query.repository;

import com.orders.query.model.OrderSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderSummaryRepository extends JpaRepository<OrderSummary, UUID> {
    Page<OrderSummary> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(
            String firstName, String lastName, Pageable pageable);
}
