package com.orders.query.rebuild;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.eventstore.EventRecord;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.upcasting.UpcasterChain;
import com.orders.query.projection.OrderProjectionHandler;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import com.orders.query.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionRebuilderService {
    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilderService.class);
    private static final int BATCH_SIZE = 500;
    private static final String LISTENER_ID = "order-projection-listener";

    private final OrderSummaryRepository summaryRepo;
    private final OrderItemViewRepository itemRepo;
    private final ProcessedEventRepository processedRepo;
    private final EventStoreRepository eventStoreRepo;
    private final OrderProjectionHandler projectionHandler;
    private final UpcasterChain upcasterChain;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

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
        MessageListenerContainer listenerContainer = getListenerContainer();
        if (listenerContainer != null && listenerContainer.isRunning()) {
            log.info("Pausing Kafka listener '{}' during rebuild...", LISTENER_ID);
            listenerContainer.pause();
        }

        try {
            itemRepo.deleteAll();
            summaryRepo.deleteAll();
            processedRepo.deleteAll();

            long totalProcessed = 0;
            Pageable pageable = PageRequest.of(0, BATCH_SIZE);
            Slice<EventRecord> slice;

            do {
                slice = eventStoreRepo.findAllByOrderByOccurredAtAscSequenceNumberAsc(pageable);
                for (EventRecord record : slice.getContent()) {
                    DomainEvent event = upcasterChain.deserialize(
                            record.getEventType(), record.getEventVersion(), record.getPayload(), objectMapper);
                    projectionHandler.handleEvent(event);
                    processedRepo.save(new com.orders.query.model.ProcessedEvent(event.getEventId()));
                    totalProcessed++;
                }
                pageable = slice.nextPageable();
            } while (slice.hasNext());

            log.info("Projection rebuild complete. Processed {} events.", totalProcessed);
        } finally {
            if (listenerContainer != null && listenerContainer.isPauseRequested()) {
                log.info("Resuming Kafka listener '{}' after rebuild.", LISTENER_ID);
                listenerContainer.resume();
            }
        }
    }

    private MessageListenerContainer getListenerContainer() {
        if (kafkaListenerEndpointRegistry == null) {
            return null;
        }
        return kafkaListenerEndpointRegistry.getListenerContainer(LISTENER_ID);
    }
}
