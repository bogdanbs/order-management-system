package com.orders.command.application.handlers;

import com.orders.TestcontainersConfiguration;
import com.orders.command.application.commands.AddOrderItemCommand;
import com.orders.command.application.commands.CreateOrderCommand;
import com.orders.command.domain.Order;
import com.orders.command.domain.OrderItem;
import com.orders.infrastructure.concurrency.OptimisticConcurrencyException;
import com.orders.infrastructure.eventstore.EventStore;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.outbox.OutboxRecord;
import com.orders.infrastructure.outbox.OutboxRepository;
import com.orders.infrastructure.upcasting.UpcasterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OrderCommandHandlerIT {
    @Autowired
    private OrderCommandHandler handler;

    @MockitoSpyBean
    private EventStore eventStore;

    @Autowired
    private EventStoreRepository eventRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private UpcasterChain upcasterChain;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void retriesWithFreshStateWhenAnotherWriterCommitsFirst() {
        UUID orderId = createOrder();
        injectConflicts(orderId, 1);

        handler.addItem(new AddOrderItemCommand(orderId, UUID.randomUUID(), "Notebook", 1, new BigDecimal("4.50")));

        Order order = Order.rehydrate(eventStore.loadEvents(orderId, upcasterChain));
        assertThat(order.getItems()).extracting(OrderItem::getProductName)
                .containsExactlyInAnyOrder("Pencil", "Notebook");
        assertThat(eventRepository.countByAggregateId(orderId)).isEqualTo(3);
        // The concurrent writer bypasses the outbox; the failed attempt must not leave a row behind.
        assertThat(outboxRepository.findAll()).filteredOn(r -> r.getAggregateId().equals(orderId))
                .extracting(OutboxRecord::getEventType)
                .containsExactlyInAnyOrder("OrderCreated", "OrderItemAdded");
    }

    @Test
    void givesUpWithOptimisticConcurrencyExceptionWhenEveryAttemptConflicts() {
        UUID orderId = createOrder();
        injectConflicts(orderId, 3);

        assertThatThrownBy(() -> handler.addItem(
                new AddOrderItemCommand(orderId, UUID.randomUUID(), "Notebook", 1, new BigDecimal("4.50"))))
                .isInstanceOf(OptimisticConcurrencyException.class);

        Order order = Order.rehydrate(eventStore.loadEvents(orderId, upcasterChain));
        assertThat(order.getItems()).extracting(OrderItem::getProductName).containsOnly("Pencil");
    }

    private UUID createOrder() {
        UUID orderId = UUID.randomUUID();
        handler.createOrder(new CreateOrderCommand(orderId, "Ada", "Lovelace", "12 St James's Square"));
        return orderId;
    }

    /**
     * Before each of the next {@code conflicts} appends for the order, a competing writer commits an event at the
     * same expected version in its own transaction, so the handler's insert hits the unique sequence constraint.
     */
    private void injectConflicts(UUID orderId, int conflicts) {
        TransactionTemplate competingTx = new TransactionTemplate(transactionManager);
        competingTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicInteger remaining = new AtomicInteger(conflicts);
        AtomicBoolean competing = new AtomicBoolean();

        doAnswer(invocation -> {
            if (!competing.get() && remaining.getAndDecrement() > 0) {
                long expectedVersion = invocation.getArgument(3);
                competing.set(true);
                try {
                    competingTx.executeWithoutResult(status -> {
                        Order other = Order.rehydrate(eventStore.loadEvents(orderId, upcasterChain));
                        other.addItem(UUID.randomUUID(), "Pencil", 1, new BigDecimal("0.80"));
                        eventStore.append(other.drainPendingEvents(), orderId, "Order", expectedVersion);
                    });
                } finally {
                    competing.set(false);
                }
            }
            return invocation.callRealMethod();
        }).when(eventStore).append(any(), eq(orderId), anyString(), anyLong());
    }
}
