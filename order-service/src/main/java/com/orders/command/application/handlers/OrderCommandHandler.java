package com.orders.command.application.handlers;

import com.orders.command.application.commands.AddOrderItemCommand;
import com.orders.command.application.commands.CancelOrderCommand;
import com.orders.command.application.commands.ConfirmPaymentCommand;
import com.orders.command.application.commands.CreateOrderCommand;
import com.orders.command.application.commands.RemoveOrderItemCommand;
import com.orders.command.application.commands.SubmitOrderCommand;
import com.orders.command.domain.Order;
import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.concurrency.RetryOnConcurrency;
import com.orders.infrastructure.eventstore.EventStore;
import com.orders.infrastructure.outbox.OutboxService;
import com.orders.infrastructure.snapshot.SnapshotRecord;
import com.orders.infrastructure.snapshot.SnapshotService;
import com.orders.infrastructure.upcasting.UpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderCommandHandler {
    private static final Logger log = LoggerFactory.getLogger(OrderCommandHandler.class);

    private final EventStore eventStore;
    private final OutboxService outboxService;
    private final SnapshotService snapshotService;
    private final UpcasterChain upcasterChain;

    public OrderCommandHandler(EventStore eventStore,
                               OutboxService outboxService,
                               SnapshotService snapshotService,
                               UpcasterChain upcasterChain) {
        this.eventStore = eventStore;
        this.outboxService = outboxService;
        this.snapshotService = snapshotService;
        this.upcasterChain = upcasterChain;
    }

    @RetryOnConcurrency
    public UUID createOrder(CreateOrderCommand cmd) {
        log.info("Creating order {} for {} {}", cmd.orderId(), cmd.firstName(), cmd.lastName());
        Order order = Order.create(cmd.orderId(), cmd.firstName(), cmd.lastName(), cmd.shippingAddress());
        List<DomainEvent> events = order.drainPendingEvents();
        eventStore.append(events, cmd.orderId(), "Order", 0);
        outboxService.write(events, cmd.orderId(), "Order");
        snapshotService.saveIfThreshold(order, cmd.orderId(), "Order");
        log.info("Order {} created", cmd.orderId());
        return cmd.orderId();
    }

    @RetryOnConcurrency
    public void addItem(AddOrderItemCommand cmd) {
        log.info("Adding item {} (qty={}) to order {}", cmd.productName(), cmd.quantity(), cmd.orderId());
        Order order = loadOrder(cmd.orderId());
        order.addItem(cmd.productId(), cmd.productName(), cmd.quantity(), cmd.unitPrice());
        persistChanges(order, cmd.orderId(), "Order");
    }

    @RetryOnConcurrency
    public void removeItem(RemoveOrderItemCommand cmd) {
        log.info("Removing item {} from order {}", cmd.productId(), cmd.orderId());
        Order order = loadOrder(cmd.orderId());
        order.removeItem(cmd.productId(), cmd.quantity());
        persistChanges(order, cmd.orderId(), "Order");
    }

    @RetryOnConcurrency
    public void submit(SubmitOrderCommand cmd) {
        log.info("Submitting order {}", cmd.orderId());
        Order order = loadOrder(cmd.orderId());
        order.submit();
        persistChanges(order, cmd.orderId(), "Order");
        log.info("Order {} submitted", cmd.orderId());
    }

    @RetryOnConcurrency
    public void confirmPayment(ConfirmPaymentCommand cmd) {
        log.info("Confirming payment {} for order {}", cmd.paymentId(), cmd.orderId());
        Order order = loadOrder(cmd.orderId());
        order.confirmPayment(cmd.paymentId(), cmd.amount());
        persistChanges(order, cmd.orderId(), "Order");
        log.info("Order {} payment confirmed", cmd.orderId());
    }

    @RetryOnConcurrency
    public void cancel(CancelOrderCommand cmd) {
        log.info("Cancelling order {}: {}", cmd.orderId(), cmd.reason());
        Order order = loadOrder(cmd.orderId());
        order.cancel(cmd.reason());
        persistChanges(order, cmd.orderId(), "Order");
        log.info("Order {} cancelled", cmd.orderId());
    }

    private Order loadOrder(UUID orderId) {
        Optional<SnapshotRecord> snapshotOpt = snapshotService.loadSnapshot(orderId);
        if (snapshotOpt.isPresent()) {
            SnapshotRecord snap = snapshotOpt.get();
            Order fromSnapshot = snapshotService.deserializeOrder(snap);
            List<DomainEvent> subsequent = eventStore.loadEventsAfter(orderId, snap.getSequenceNumber(), upcasterChain);
            return Order.rehydrateFrom(fromSnapshot, subsequent);
        }
        List<DomainEvent> events = eventStore.loadEvents(orderId, upcasterChain);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        return Order.rehydrate(events);
    }

    private void persistChanges(Order order, UUID orderId, String aggregateType) {
        List<DomainEvent> events = order.drainPendingEvents();
        eventStore.append(events, orderId, aggregateType, order.getVersion() - events.size());
        outboxService.write(events, orderId, aggregateType);
        snapshotService.saveIfThreshold(order, orderId, aggregateType);
    }
}
