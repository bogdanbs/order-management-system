package com.orders.query.rebuild;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.eventstore.EventRecord;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.upcasting.UpcasterChain;
import com.orders.query.model.ProcessedEvent;
import com.orders.query.projection.OrderProjectionHandler;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import com.orders.query.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectionRebuilderServiceTest {

    @Test
    void rebuildsProjectionsInBatchesAndPopulatesProcessedEvents() {
        OrderSummaryRepository summaryRepo = mock(OrderSummaryRepository.class);
        OrderItemViewRepository itemRepo = mock(OrderItemViewRepository.class);
        ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
        EventStoreRepository eventStoreRepo = mock(EventStoreRepository.class);
        OrderProjectionHandler projectionHandler = mock(OrderProjectionHandler.class);
        UpcasterChain upcasterChain = mock(UpcasterChain.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ProjectionRebuilderService service = new ProjectionRebuilderService(
                summaryRepo, itemRepo, processedRepo, eventStoreRepo, projectionHandler, upcasterChain, objectMapper);

        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        EventRecord record = new EventRecord();
        record.setId(eventId);
        record.setAggregateId(orderId);
        record.setAggregateType("Order");
        record.setEventType("OrderCreated");
        record.setEventVersion(2);
        record.setSequenceNumber(1L);
        record.setPayload("{}");
        record.setOccurredAt(Instant.now());

        DomainEvent domainEvent = mock(DomainEvent.class);
        when(domainEvent.getEventId()).thenReturn(eventId);
        when(domainEvent.getAggregateId()).thenReturn(orderId);

        when(eventStoreRepo.findAllByOrderByOccurredAtAscSequenceNumberAsc(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(record)));

        when(upcasterChain.deserialize(eq("OrderCreated"), eq(2), eq("{}"), eq(objectMapper)))
                .thenReturn(domainEvent);

        service.rebuild();

        verify(itemRepo).deleteAll();
        verify(summaryRepo).deleteAll();
        verify(processedRepo).deleteAll();

        verify(projectionHandler).handleEvent(domainEvent);
        verify(processedRepo).save(any(ProcessedEvent.class));
    }
}
