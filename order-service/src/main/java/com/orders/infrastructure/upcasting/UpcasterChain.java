package com.orders.infrastructure.upcasting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.command.domain.events.OrderCancelledEvent;
import com.orders.command.domain.events.OrderCreatedEventV2;
import com.orders.command.domain.events.OrderItemAddedEvent;
import com.orders.command.domain.events.OrderItemRemovedEvent;
import com.orders.command.domain.events.OrderPaymentConfirmedEvent;
import com.orders.command.domain.events.OrderSubmittedEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UpcasterChain {
    private final Map<String, Upcaster> upcasters;
    private final Map<String, Class<? extends DomainEvent>> eventRegistry;

    public UpcasterChain(List<Upcaster> upcasterList) {
        this.upcasters = upcasterList.stream()
                .collect(Collectors.toMap(
                        u -> u.sourceEventType() + "_v" + u.sourceVersion(),
                        u -> u));
        this.eventRegistry = buildEventRegistry();
    }

    public DomainEvent deserialize(String eventType, int eventVersion, String rawJson, ObjectMapper objectMapper) {
        String key = eventType + "_v" + eventVersion;
        if (upcasters.containsKey(key)) {
            try {
                return upcasters.get(key).upcast(rawJson, objectMapper);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Upcasting failed for " + key, e);
            }
        }
        Class<? extends DomainEvent> cls = eventRegistry.get(key);
        if (cls == null) {
            throw new IllegalArgumentException("No event class registered for: " + eventType + " v" + eventVersion);
        }
        try {
            return objectMapper.readValue(rawJson, cls);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Deserialization failed for " + eventType, e);
        }
    }

    private Map<String, Class<? extends DomainEvent>> buildEventRegistry() {
        Map<String, Class<? extends DomainEvent>> map = new HashMap<>();
        map.put("OrderCreated_v2", OrderCreatedEventV2.class);
        map.put("OrderItemAdded_v1", OrderItemAddedEvent.class);
        map.put("OrderItemRemoved_v1", OrderItemRemovedEvent.class);
        map.put("OrderSubmitted_v1", OrderSubmittedEvent.class);
        map.put("OrderPaymentConfirmed_v1", OrderPaymentConfirmedEvent.class);
        map.put("OrderCancelled_v1", OrderCancelledEvent.class);
        return map;
    }
}
