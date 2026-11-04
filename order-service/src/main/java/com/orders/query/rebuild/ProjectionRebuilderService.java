package com.orders.query.rebuild;

import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.eventstore.EventRecord;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.upcasting.UpcasterChain;
import com.orders.query.projection.OrderProjectionHandler;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import com.orders.query.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectionRebuilderService {
    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilderService.class);

    private final OrderSummaryRepository summaryRepo;
    private final OrderItemViewRepository itemRepo;
    private final ProcessedEventRepository processedRepo;
    private final EventStoreRepository eventStoreRepo;
    private final OrderProjectionHandler projectionHandler;
    private final UpcasterChain upcasterChain;
    private final ObjectMapper objectMapper;

    public ProjectionRebuilderService(OrderSummaryRepository summaryRepo,
                                      OrderItemViewRepository itemRepo,
                                      ProcessedEventRepository processedRepo,
                                      EventStoreRepository eventStoreRepo,
                                      OrderProjectionHandler projectionHandler,
                                      UpcasterChain upcasterChain,
                                      ObjectMapper objectMapper) {
        this.summaryRepo = summaryRepo;
        this.itemRepo = itemRepo;
        this.processedRepo = processedRepo;
        this.eventStoreRepo = eventStoreRepo;
        this.projectionHandler = projectionHandler;
        this.upcasterChain = upcasterChain;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void rebuild() {
        log.info("Starting projection rebuild...");
        itemRepo.deleteAll();
        summaryRepo.deleteAll();
        processedRepo.deleteAll();

        List<EventRecord> allEvents = eventStoreRepo.findAllByOrderByOccurredAtAsc();
        for (EventRecord record : allEvents) {
            DomainEvent event = upcasterChain.deserialize(
                    record.getEventType(), record.getEventVersion(), record.getPayload(), objectMapper);
            projectionHandler.handleEvent(event);
        }
        log.info("Projection rebuild complete. Processed {} events.", allEvents.size());
    }
}
