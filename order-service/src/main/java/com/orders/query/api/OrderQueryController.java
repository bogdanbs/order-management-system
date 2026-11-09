package com.orders.query.api;

import com.orders.command.domain.OrderNotFoundException;
import com.orders.infrastructure.eventstore.EventRecord;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.query.model.OrderItemView;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {
    private final OrderSummaryRepository summaryRepo;
    private final OrderItemViewRepository itemRepo;
    private final EventStoreRepository eventStoreRepo;

    public OrderQueryController(OrderSummaryRepository summaryRepo,
                                OrderItemViewRepository itemRepo,
                                EventStoreRepository eventStoreRepo) {
        this.summaryRepo = summaryRepo;
        this.itemRepo = itemRepo;
        this.eventStoreRepo = eventStoreRepo;
    }

    @GetMapping
    public List<OrderSummary> list() {
        return summaryRepo.findAll();
    }

    @GetMapping("/customer")
    public List<OrderSummary> findByCustomer(@RequestParam String firstName,
                                             @RequestParam String lastName) {
        return summaryRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseOrderByCreatedAtDesc(firstName, lastName);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderSummary> get(@PathVariable UUID id) {
        return summaryRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @GetMapping("/{id}/items")
    public List<OrderItemView> items(@PathVariable UUID id) {
        return itemRepo.findByOrderId(id);
    }

    @GetMapping("/{id}/events")
    public List<EventRecord> events(@PathVariable UUID id) {
        return eventStoreRepo.findByAggregateIdOrderBySequenceNumberAsc(id);
    }
}
