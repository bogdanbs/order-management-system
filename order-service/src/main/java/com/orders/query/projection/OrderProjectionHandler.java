package com.orders.query.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.command.domain.events.OrderCancelledEvent;
import com.orders.command.domain.events.OrderCreatedEventV2;
import com.orders.command.domain.events.OrderItemAddedEvent;
import com.orders.command.domain.events.OrderItemRemovedEvent;
import com.orders.command.domain.events.OrderPaymentConfirmedEvent;
import com.orders.command.domain.events.OrderSubmittedEvent;
import com.orders.infrastructure.messaging.KafkaTopics;
import com.orders.infrastructure.upcasting.UpcasterChain;
import com.orders.query.model.OrderItemView;
import com.orders.query.model.OrderSummary;
import com.orders.query.model.ProcessedEvent;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import com.orders.query.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderProjectionHandler {
    private final OrderSummaryRepository summaryRepo;
    private final OrderItemViewRepository itemRepo;
    private final ProcessedEventRepository processedRepo;
    private final UpcasterChain upcasterChain;
    private final ObjectMapper objectMapper;

    public OrderProjectionHandler(OrderSummaryRepository summaryRepo,
                                  OrderItemViewRepository itemRepo,
                                  ProcessedEventRepository processedRepo,
                                  UpcasterChain upcasterChain,
                                  ObjectMapper objectMapper) {
        this.summaryRepo = summaryRepo;
        this.itemRepo = itemRepo;
        this.processedRepo = processedRepo;
        this.upcasterChain = upcasterChain;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            id = "order-projection-listener",
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = "order-projection",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        String payload = record.value();
        JsonNode root = objectMapper.readTree(payload);
        String eventType = root.get("eventType").asText();
        int eventVersion = root.has("eventVersion") ? root.get("eventVersion").asInt(1) : 1;
        UUID eventId = UUID.fromString(root.get("eventId").asText());

        if (processedRepo.existsById(eventId)) {
            return;
        }

        DomainEvent event = upcasterChain.deserialize(eventType, eventVersion, payload, objectMapper);
        handleEvent(event);
        processedRepo.save(new ProcessedEvent(eventId));
    }

    public void handleEvent(DomainEvent event) {
        UUID orderId = event.getAggregateId();

        switch (event) {
            case OrderCreatedEventV2 e -> {
                OrderSummary summary = new OrderSummary();
                summary.setOrderId(orderId);
                summary.setFirstName(e.getFirstName());
                summary.setLastName(e.getLastName());
                summary.setShippingAddress(e.getShippingAddress());
                summary.setStatus("DRAFT");
                summary.setCreatedAt(e.getOccurredAt());
                summaryRepo.save(summary);

            }
            case OrderItemAddedEvent e -> {
                OrderSummary summary = summaryRepo.findById(orderId)
                        .orElseThrow(() -> new IllegalStateException("Order summary not found: " + orderId));
                summary.setTotalAmount(
                        summary.getTotalAmount().add(e.getUnitPrice().multiply(BigDecimal.valueOf(e.getQuantity()))));
                summary.setUpdatedAt(Instant.now());

                Optional<OrderItemView> existingItem = itemRepo.findByOrderIdAndProductId(orderId, e.getProductId());

                if (existingItem.isPresent()) {
                    OrderItemView item = existingItem.get();
                    summary.setTotalAmount(summary.getTotalAmount()
                            .subtract(e.getUnitPrice().multiply(BigDecimal.valueOf(e.getQuantity())))
                            .add(item.getUnitPrice().multiply(BigDecimal.valueOf(e.getQuantity()))));
                    item.setQuantity(item.getQuantity() + e.getQuantity());
                    itemRepo.save(item);
                } else {
                    summary.setItemCount(summary.getItemCount() + 1);
                    OrderItemView item = new OrderItemView();
                    item.setOrderId(orderId);
                    item.setProductId(e.getProductId());
                    item.setProductName(e.getProductName());
                    item.setQuantity(e.getQuantity());
                    item.setUnitPrice(e.getUnitPrice());
                    itemRepo.save(item);
                }
                summaryRepo.save(summary);

            }
            case OrderItemRemovedEvent e -> {
                OrderSummary summary = summaryRepo.findById(orderId)
                        .orElseThrow(() -> new IllegalStateException("Order summary not found: " + orderId));
                OrderItemView item = itemRepo.findByOrderIdAndProductId(orderId, e.getProductId()).orElseThrow(
                        () -> new IllegalStateException(
                                "Order item not found for order " + orderId + ": " + e.getProductId()));
                if (e.getQuantity() <= 0 || e.getQuantity() > item.getQuantity()) {
                    throw new IllegalStateException("Removal quantity exceeds projected item quantity");
                }

                summary.setTotalAmount(summary.getTotalAmount()
                        .subtract(item.getUnitPrice().multiply(BigDecimal.valueOf(e.getQuantity()))));
                summary.setUpdatedAt(Instant.now());
                int remainingQuantity = item.getQuantity() - e.getQuantity();
                if (remainingQuantity == 0) {
                    summary.setItemCount(summary.getItemCount() - 1);
                    itemRepo.delete(item);
                } else {
                    item.setQuantity(remainingQuantity);
                    itemRepo.save(item);
                }
                summaryRepo.save(summary);

            }
            case OrderSubmittedEvent orderSubmittedEvent -> updateStatus(orderId, "SUBMITTED");
            case OrderPaymentConfirmedEvent orderPaymentConfirmedEvent -> updateStatus(orderId, "PAID");
            case OrderCancelledEvent orderCancelledEvent -> updateStatus(orderId, "CANCELLED");
            default -> {
            }
        }
    }

    private void updateStatus(UUID orderId, String status) {
        summaryRepo.findById(orderId).ifPresent(s -> {
            s.setStatus(status);
            s.setUpdatedAt(Instant.now());
            summaryRepo.save(s);
        });
    }
}
