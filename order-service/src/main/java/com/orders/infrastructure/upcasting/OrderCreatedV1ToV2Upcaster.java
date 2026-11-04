package com.orders.infrastructure.upcasting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.command.domain.events.OrderCreatedEventV1;
import com.orders.command.domain.events.OrderCreatedEventV2;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedV1ToV2Upcaster implements Upcaster {

    @Override
    public String sourceEventType() {
        return "OrderCreated";
    }

    @Override
    public int sourceVersion() {
        return 1;
    }

    @Override
    public DomainEvent upcast(String rawJson, ObjectMapper objectMapper) throws JsonProcessingException {
        OrderCreatedEventV1 v1 = objectMapper.readValue(rawJson, OrderCreatedEventV1.class);
        OrderCreatedEventV2 v2 = new OrderCreatedEventV2();
        v2.setAggregateId(v1.getAggregateId());
        v2.setEventId(v1.getEventId());
        v2.setOccurredAt(v1.getOccurredAt());
        v2.setMetadata(v1.getMetadata());
        v2.setShippingAddress(v1.getShippingAddress());

        String name = v1.getCustomerName() != null ? v1.getCustomerName() : "";
        int space = name.indexOf(' ');
        if (space > 0) {
            v2.setFirstName(name.substring(0, space));
            v2.setLastName(name.substring(space + 1));
        } else {
            v2.setFirstName(name);
            v2.setLastName("");
        }
        return v2;
    }
}
