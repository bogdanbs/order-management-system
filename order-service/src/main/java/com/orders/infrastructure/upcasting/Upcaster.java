package com.orders.infrastructure.upcasting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;

public interface Upcaster {
    String sourceEventType();

    int sourceVersion();

    DomainEvent upcast(String rawJson, ObjectMapper objectMapper) throws JsonProcessingException;
}
