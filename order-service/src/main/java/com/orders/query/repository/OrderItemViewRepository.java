package com.orders.query.repository;

import com.orders.query.model.OrderItemView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemViewRepository extends JpaRepository<OrderItemView, UUID> {
    List<OrderItemView> findByOrderId(UUID orderId);

    Optional<OrderItemView> findByOrderIdAndProductId(UUID orderId, UUID productId);

    @Transactional
    void deleteByOrderId(UUID orderId);
}
