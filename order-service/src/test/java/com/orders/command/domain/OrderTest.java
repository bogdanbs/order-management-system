package com.orders.command.domain;

import com.orders.command.domain.events.OrderItemRemovedEvent;
import com.orders.command.domain.events.OrderPaymentConfirmedEvent;
import com.orders.command.domain.events.DomainEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {
    @Test
    void removingOneUnitKeepsTheRemainingQuantity() {
        UUID productId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "Ada", "Lovelace", "London");
        order.addItem(productId, "Book", 3, new BigDecimal("12.50"));

        order.removeItem(productId, 1);

        assertEquals(2, order.getItems().getFirst().getQuantity());
        OrderItemRemovedEvent event = (OrderItemRemovedEvent) order.drainPendingEvents().getLast();
        assertEquals(1, event.getQuantity());
    }

    @Test
    void removingTheRemainingQuantityDeletesTheLineAndExcessRemovalIsRejected() {
        UUID productId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "Ada", "Lovelace", "London");
        order.addItem(productId, "Book", 2, new BigDecimal("12.50"));

        assertThrows(IllegalArgumentException.class, () -> order.removeItem(productId, 3));
        order.removeItem(productId, 2);

        assertEquals(0, order.getItems().size());
    }

    @Test
    void paymentMustMatchTotalAndSamePaymentRetryIsIdempotent() {
        UUID paymentId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "Ada", "Lovelace", "London");
        List<DomainEvent> history = new ArrayList<>(order.drainPendingEvents());
        order.addItem(UUID.randomUUID(), "Book", 2, new BigDecimal("12.50"));
        history.addAll(order.drainPendingEvents());
        order.submit();
        history.addAll(order.drainPendingEvents());

        assertThrows(IllegalArgumentException.class,
                () -> order.confirmPayment(paymentId, new BigDecimal("24.99")));
        assertEquals(0, order.drainPendingEvents().size());

        order.confirmPayment(paymentId, new BigDecimal("25.00"));
        List<DomainEvent> paymentEvents = order.drainPendingEvents();
        history.addAll(paymentEvents);
        assertEquals(1, paymentEvents.stream()
                .filter(OrderPaymentConfirmedEvent.class::isInstance)
                .count());

        Order rehydrated = Order.rehydrate(history);
        rehydrated.confirmPayment(paymentId, new BigDecimal("25.0"));
        assertEquals(0, rehydrated.drainPendingEvents().size());
        assertThrows(IllegalStateException.class,
                () -> rehydrated.confirmPayment(UUID.randomUUID(), new BigDecimal("25.00")));
    }
}
