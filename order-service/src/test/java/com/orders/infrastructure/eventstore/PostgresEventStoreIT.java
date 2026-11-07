package com.orders.infrastructure.eventstore;

import com.orders.TestcontainersConfiguration;
import com.orders.command.domain.Order;
import com.orders.command.domain.events.DomainEvent;
import com.orders.command.domain.events.OrderCreatedEventV2;
import com.orders.command.domain.events.OrderItemAddedEvent;
import com.orders.infrastructure.concurrency.OptimisticConcurrencyException;
import com.orders.infrastructure.upcasting.UpcasterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class PostgresEventStoreIT {
    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventStoreRepository repository;

    @Autowired
    private UpcasterChain upcasterChain;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void appendsAndLoadsEventsInSequenceOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, "Ada", "Lovelace", "12 St James's Square");
        order.addItem(UUID.randomUUID(), "Notebook", 2, new BigDecimal("4.50"));

        append(order.drainPendingEvents(), orderId, 0);

        List<DomainEvent> loaded = eventStore.loadEvents(orderId, upcasterChain);
        assertThat(loaded).hasExactlyElementsOfTypes(OrderCreatedEventV2.class, OrderItemAddedEvent.class);
        assertThat(((OrderCreatedEventV2) loaded.get(0)).getLastName()).isEqualTo("Lovelace");
        assertThat(repository.findByAggregateIdOrderBySequenceNumberAsc(orderId))
                .extracting(EventRecord::getSequenceNumber)
                .containsExactly(1L, 2L);
    }

    @Test
    void loadsOnlyEventsAfterTheGivenSequenceNumber() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, "Ada", "Lovelace", "12 St James's Square");
        order.addItem(UUID.randomUUID(), "Notebook", 1, new BigDecimal("4.50"));
        order.addItem(UUID.randomUUID(), "Pencil", 3, new BigDecimal("0.80"));
        append(order.drainPendingEvents(), orderId, 0);

        List<DomainEvent> after = eventStore.loadEventsAfter(orderId, 1, upcasterChain);

        assertThat(after).hasSize(2).allMatch(OrderItemAddedEvent.class::isInstance);
    }

    @Test
    void rejectsAppendWhenAnotherWriterAlreadyUsedTheExpectedVersion() {
        UUID orderId = UUID.randomUUID();
        append(Order.create(orderId, "Ada", "Lovelace", "12 St James's Square").drainPendingEvents(), orderId, 0);

        // Two writers loaded version 1; the first one wins.
        Order first = Order.rehydrate(eventStore.loadEvents(orderId, upcasterChain));
        Order second = Order.rehydrate(eventStore.loadEvents(orderId, upcasterChain));
        first.addItem(UUID.randomUUID(), "Notebook", 1, new BigDecimal("4.50"));
        second.addItem(UUID.randomUUID(), "Pencil", 1, new BigDecimal("0.80"));
        append(first.drainPendingEvents(), orderId, 1);

        assertThatThrownBy(() -> append(second.drainPendingEvents(), orderId, 1))
                .isInstanceOf(OptimisticConcurrencyException.class);
        assertThat(repository.countByAggregateId(orderId)).isEqualTo(2);
    }

    // Appends run inside a transaction, as they do in OrderCommandHandler.
    private void append(List<DomainEvent> events, UUID aggregateId, long expectedVersion) {
        tx.executeWithoutResult(status -> eventStore.append(events, aggregateId, "Order", expectedVersion));
    }
}
