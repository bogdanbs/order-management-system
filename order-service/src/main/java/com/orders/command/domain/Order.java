package com.orders.command.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orders.command.domain.events.DomainEvent;
import com.orders.command.domain.events.OrderCancelledEvent;
import com.orders.command.domain.events.OrderCreatedEventV2;
import com.orders.command.domain.events.OrderItemAddedEvent;
import com.orders.command.domain.events.OrderItemRemovedEvent;
import com.orders.command.domain.events.OrderPaymentConfirmedEvent;
import com.orders.command.domain.events.OrderSubmittedEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Order {
    private UUID id;
    private OrderStatus status;
    private String firstName;
    private String lastName;
    private String shippingAddress;
    private final List<OrderItem> items = new ArrayList<>();
    private UUID paymentId;
    private long version = 0;

    @JsonIgnore
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    protected Order() {
    }

    public static Order create(UUID orderId, String firstName, String lastName, String shippingAddress) {
        Order order = new Order();
        order.id = orderId;
        OrderCreatedEventV2 event = new OrderCreatedEventV2();
        event.setAggregateId(orderId);
        event.setFirstName(firstName);
        event.setLastName(lastName);
        event.setShippingAddress(shippingAddress);
        order.applyAndRecord(event);
        return order;
    }

    public void addItem(UUID productId, String productName, int quantity, BigDecimal unitPrice) {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Can only add items to DRAFT orders");
        }
        items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .filter(item -> item.getUnitPrice().compareTo(unitPrice) != 0)
                .ifPresent(item -> {
                    throw new IllegalArgumentException(
                            "Unit price cannot change for a product already in the order");
                });
        OrderItemAddedEvent e = new OrderItemAddedEvent();
        e.setAggregateId(id);
        e.setProductId(productId);
        e.setProductName(productName);
        e.setQuantity(quantity);
        e.setUnitPrice(unitPrice);
        applyAndRecord(e);
    }

    public void removeItem(UUID productId, int quantity) {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Can only remove items from DRAFT orders");
        }
        OrderItem item = items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + productId));
        if (quantity <= 0 || quantity > item.getQuantity()) {
            throw new IllegalArgumentException("Removal quantity must be between 1 and the item quantity");
        }
        OrderItemRemovedEvent e = new OrderItemRemovedEvent();
        e.setAggregateId(id);
        e.setProductId(productId);
        e.setQuantity(quantity);
        applyAndRecord(e);
    }

    public void submit() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Order must be DRAFT to submit");
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot submit empty order");
        }
        OrderSubmittedEvent e = new OrderSubmittedEvent();
        e.setAggregateId(id);
        applyAndRecord(e);
    }

    public void confirmPayment(UUID paymentId, BigDecimal amount) {
        if (paymentId == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment ID and a positive amount are required");
        }
        if (status == OrderStatus.PAID) {
            if (paymentId.equals(this.paymentId) && amount.compareTo(totalAmount()) == 0) {
                return;
            }
            throw new IllegalStateException("Payment has already been confirmed for this order");
        }
        if (status != OrderStatus.SUBMITTED) {
            throw new IllegalStateException("Order must be SUBMITTED for payment");
        }
        if (amount.compareTo(totalAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount must equal the order total");
        }
        OrderPaymentConfirmedEvent e = new OrderPaymentConfirmedEvent();
        e.setAggregateId(id);
        e.setPaymentId(paymentId);
        e.setAmount(amount);
        applyAndRecord(e);
    }

    public void cancel(String reason) {
        if (status == OrderStatus.PAID || status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Cannot cancel order in status: " + status);
        }
        OrderCancelledEvent e = new OrderCancelledEvent();
        e.setAggregateId(id);
        e.setReason(reason);
        applyAndRecord(e);
    }

    public static Order rehydrate(List<DomainEvent> events) {
        Order order = new Order();
        for (DomainEvent event : events) {
            order.apply(event);
            order.version++;
        }
        return order;
    }

    public static Order rehydrateFrom(Order base, List<DomainEvent> additional) {
        for (DomainEvent event : additional) {
            base.apply(event);
            base.version++;
        }
        return base;
    }

    private void applyAndRecord(DomainEvent event) {
        apply(event);
        pendingEvents.add(event);
        version++;
    }

    private void apply(DomainEvent event) {
        if (event instanceof OrderCreatedEventV2 e) {
            this.id = e.getAggregateId();
            this.firstName = e.getFirstName();
            this.lastName = e.getLastName();
            this.shippingAddress = e.getShippingAddress();
            this.status = OrderStatus.DRAFT;
        } else if (event instanceof OrderItemAddedEvent e) {
            items.stream()
                    .filter(i -> i.getProductId().equals(e.getProductId()))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.setQuantity(existing.getQuantity() + e.getQuantity()),
                            () -> items.add(new OrderItem(e.getProductId(), e.getProductName(), e.getQuantity(), e.getUnitPrice()))
                    );
        } else if (event instanceof OrderItemRemovedEvent e) {
            OrderItem item = items.stream()
                    .filter(i -> i.getProductId().equals(e.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Item not found: " + e.getProductId()));
            if (e.getQuantity() <= 0 || e.getQuantity() > item.getQuantity()) {
                throw new IllegalStateException("Removal quantity exceeds item quantity");
            }
            int remainingQuantity = item.getQuantity() - e.getQuantity();
            if (remainingQuantity == 0) {
                items.remove(item);
            } else {
                item.setQuantity(remainingQuantity);
            }
        } else if (event instanceof OrderSubmittedEvent) {
            this.status = OrderStatus.SUBMITTED;
        } else if (event instanceof OrderPaymentConfirmedEvent e) {
            this.status = OrderStatus.PAID;
            this.paymentId = e.getPaymentId();
        } else if (event instanceof OrderCancelledEvent) {
            this.status = OrderStatus.CANCELLED;
        }
    }

    private BigDecimal totalAmount() {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<DomainEvent> drainPendingEvents() {
        List<DomainEvent> drained = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public long getVersion() {
        return version;
    }
}
